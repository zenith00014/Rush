package com.rush.app

import com.github.libretube.player.RushMediaSourceFactory

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import java.io.File
import java.io.IOException
import android.os.Handler
import android.os.Looper
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class RushPlaybackService : MediaSessionService() {
    companion object {
        @Volatile
        var activeInstance: RushPlaybackService? = null
            private set
    }

    fun hasCurrentMediaItem(): Boolean =
        mediaSession?.player?.currentMediaItem != null

    private val STREAM_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:153.0) Gecko/20100101 Firefox/153.0"
    private var mediaSession: MediaSession? = null
    private var streamCache: SimpleCache? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val recoveryAttempts = mutableMapOf<String, Int>()
    private var recoveryInProgress: String? = null
    private var activeMediaId: String? = null
    private val sleepTimerHandler = Handler(Looper.getMainLooper())
    private var sleepTimerRunnable: Runnable? = null
    private var sleepAfterCurrent = false

    fun setAutoplayEnabled(enabled: Boolean) {
        getSharedPreferences("rush_prefs", MODE_PRIVATE).edit()
            .putBoolean("autoplay_video", enabled).apply()
        (mediaSession?.player as? ExoPlayer)?.pauseAtEndOfMediaItems = !enabled
    }

    fun setSleepTimer(minutes: Int): String {
        sleepTimerRunnable?.let(sleepTimerHandler::removeCallbacks)
        sleepTimerRunnable = null
        sleepAfterCurrent = false
        if (minutes == -1) {
            sleepAfterCurrent = true
            return "Sleep timer will stop after this video or track."
        }
        if (minutes == 0) return "Sleep timer cancelled."
        require(minutes in 1..480) { "Choose a timer between 1 and 480 minutes." }
        val task = Runnable {
            mediaSession?.player?.pause()
            sleepTimerRunnable = null
            sleepAfterCurrent = false
        }
        sleepTimerRunnable = task
        sleepTimerHandler.postDelayed(task, minutes * 60_000L)
        return "Playback will stop in $minutes minutes."
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        val streamClient = RushNetwork.newBuilder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val httpFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(streamClient)
            .setUserAgent(STREAM_USER_AGENT)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to "https://www.youtube.com/",
                    "Origin" to "https://www.youtube.com/",
                    "Accept-Language" to "en-US,en;q=0.9"
                )
            )
        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, httpFactory)
        val cache = SimpleCache(
            File(cacheDir, "media3-stream-cache"),
            LeastRecentlyUsedCacheEvictor(512L * 1024L * 1024L),
            StandaloneDatabaseProvider(this)
        )
        streamCache = cache
        val cachedDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(dataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val mediaSourceFactory = RushMediaSourceFactory(cachedDataSourceFactory)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                25_000,
                90_000,
                2_000,
                2_000
            )
            .setBackBuffer(15_000, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(
            this,
            androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection.Factory(
                15_000,
                3_000,
                10_000,
                0.70f
            )
        ).apply {
            val qualityCap = getSharedPreferences("rush_prefs", MODE_PRIVATE)
                .getInt("video_quality", 0).takeIf { it > 0 } ?: Int.MAX_VALUE
            setParameters(
                buildUponParameters()
                    .setForceHighestSupportedBitrate(false)
                    .setMaxVideoSize(Int.MAX_VALUE, qualityCap)
            )
        }
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setWakeMode(androidx.media3.common.C.WAKE_MODE_LOCAL)
            .build()
        player.pauseAtEndOfMediaItems = !getSharedPreferences("rush_prefs", MODE_PRIVATE)
            .getBoolean("autoplay_video", true)
        player.setPreloadConfiguration(ExoPlayer.PreloadConfiguration(5_000_000L))
        player.setPlaybackSpeed(getSharedPreferences("rush_prefs", MODE_PRIVATE)
            .getFloat("playback_speed", 1.0f))
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onMediaItemTransition(
                mediaItem: androidx.media3.common.MediaItem?,
                reason: Int
            ) {
                val mediaId = mediaItem?.mediaId.orEmpty()
                if (sleepAfterCurrent && activeMediaId != null && activeMediaId != mediaId) {
                    sleepAfterCurrent = false
                    player.pause()
                }
                if (activeMediaId != mediaId) {
                    activeMediaId = mediaId
                    recoveryAttempts.clear()
                    if (recoveryInProgress != mediaId) recoveryInProgress = null
                    if (PlaybackRecoveryStatus.current.value?.mediaId != mediaId) {
                        PlaybackRecoveryStatus.current.value = null
                    }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: androidx.media3.common.Player.PositionInfo,
                newPosition: androidx.media3.common.Player.PositionInfo,
                reason: Int
            ) {
                val mediaId = player.currentMediaItem?.mediaId.orEmpty()
                if (mediaId.isNotBlank()) {
                    PlaybackProgress.save(
                        this@RushPlaybackService,
                        mediaId,
                        newPosition.positionMs,
                        player.duration
                    )
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_ENDED && sleepAfterCurrent) {
                    sleepAfterCurrent = false
                    player.pause()
                    return
                }
                if (playbackState == androidx.media3.common.Player.STATE_READY) {
                    val mediaId = player.currentMediaItem?.mediaId.orEmpty()
                    recoveryAttempts.remove(mediaId)
                    if (recoveryInProgress == mediaId) recoveryInProgress = null
                    if (PlaybackRecoveryStatus.current.value?.mediaId == mediaId) {
                        PlaybackRecoveryStatus.current.value = null
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                recoverPlayback(player, error)
            }
        })
        player.setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
        player.setHandleAudioBecomingNoisy(true)
        val sessionActivity = android.app.PendingIntent.getActivity(
            this,
            0,
            android.content.Intent(this, MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
        serviceScope.launch {
            while (isActive) {
                val activePlayer = mediaSession?.player
                val mediaId = activePlayer?.currentMediaItem?.mediaId.orEmpty()
                if (activePlayer != null && mediaId.isNotBlank()) {
                    PlaybackProgress.save(
                        this@RushPlaybackService,
                        mediaId,
                        activePlayer.currentPosition,
                        activePlayer.duration
                    )
                }
                delay(5_000)
            }
        }
    }

    private fun recoverPlayback(
        player: ExoPlayer,
        playbackError: androidx.media3.common.PlaybackException
    ) {
        val item = player.currentMediaItem ?: return
        val mediaId = item.mediaId
        if (mediaId.isBlank() || recoveryInProgress == mediaId) return

        val currentUri = item.localConfiguration?.uri
        if (currentUri?.scheme == "file" ||
            currentUri?.scheme == "content" ||
            currentUri?.scheme == "android.resource"
        ) return

        val extras = item.mediaMetadata.extras
        val fallbackId = mediaId.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
        val source = extras?.getString(PlaybackMetadata.SOURCE).orEmpty()
            .ifBlank { fallbackId.orEmpty() }
        if (source.isBlank()) return

        val attempt = (recoveryAttempts[mediaId] ?: 0) + 1
        if (attempt > 2) {
            PlaybackRecoveryStatus.current.value = PlaybackRecoveryMessage(
                mediaId,
                "Playback could not recover. Tap Load and play to retry."
            )
            return
        }

        val audioOnly = extras?.getBoolean(PlaybackMetadata.AUDIO_ONLY, false)
            ?: item.localConfiguration?.mimeType.orEmpty().startsWith("audio/")
        val preferences = getSharedPreferences("rush_prefs", MODE_PRIVATE)
        val savedQuality = extras?.getInt(PlaybackMetadata.QUALITY, 0) ?: 0
        val quality = preferences.getInt(
            if (audioOnly) "audio_quality" else "video_quality",
            savedQuality
        ).takeIf { it > 0 }
        val preferMp4 = false
        val currentPosition = player.currentPosition.coerceAtLeast(0L)
        val savedPosition = PlaybackProgress.resumePositionMs(this, mediaId)
        val startPosition = maxOf(currentPosition, savedPosition)

        recoveryInProgress = mediaId
        serviceScope.launch {
            var lastError: Exception = IOException(
                playbackError.message ?: "Playback error ${playbackError.errorCode}"
            )
            try {
                for (retry in attempt..2) {
                    recoveryAttempts[mediaId] = retry
                    PlaybackRecoveryStatus.current.value = PlaybackRecoveryMessage(
                        mediaId,
                        "Refreshing stream · attempt $retry of 2…"
                    )
                    delay(if (retry == 1) 600L else 1_400L)
                    if (player.currentMediaItem?.mediaId != mediaId) return@launch

                    try {
                        val preferDash = !audioOnly
                        val resolved = withContext(Dispatchers.IO) {
                            RushPlaybackEngine.resolveMedia(
                                source,
                                audioOnly,
                                if (audioOnly) null else quality,
                                if (audioOnly) quality else null,
                                false,
                                preferDash,
                                !audioOnly,
                                forceRefresh = true
                            )
                        }
                        val subtitleConfigs = if (audioOnly) emptyList() else resolved.subtitles.map { subtitle ->
                            androidx.media3.common.MediaItem.SubtitleConfiguration.Builder(
                                android.net.Uri.parse(subtitle.url)
                            )
                                .setMimeType(subtitle.mimeType)
                                .setLanguage(subtitle.languageTag)
                                .setLabel(subtitle.label)
                                .build()
                        }
                        val refreshed = androidx.media3.common.MediaItem.Builder()
                            .setMediaId(mediaId)
                            .setUri(resolved.url)
                            .setMimeType(resolved.mimeType)
                            .setMediaMetadata(item.mediaMetadata)
                            .setSubtitleConfigurations(subtitleConfigs)
                            .build()

                        val currentIndex = player.currentMediaItemIndex
                        if (currentIndex < 0 || player.currentMediaItem?.mediaId != mediaId) {
                            return@launch
                        }
                        player.replaceMediaItem(currentIndex, refreshed)
                        player.seekTo(currentIndex, startPosition)
                        PlaybackRecoveryStatus.current.value = PlaybackRecoveryMessage(
                            mediaId,
                            "Stream refreshed. Resuming playback…"
                        )
                        player.prepare()
                        player.play()
                        return@launch
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (refreshError: Exception) {
                        lastError = refreshError
                        android.util.Log.w(
                            "RushPlayback",
                            "Stream recovery attempt $retry failed for $mediaId",
                            refreshError
                        )
                    }
                }
                PlaybackRecoveryStatus.current.value = PlaybackRecoveryMessage(
                    mediaId,
                    "Playback recovery failed: ${playbackErrorMessage(lastError, "network or stream error")}. Tap Load and play to retry."
                )
            } finally {
                if (recoveryInProgress == mediaId) recoveryInProgress = null
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    /**
     * Apply Rush's background playback policy whenever the Activity leaves the foreground
     * or its task is dismissed: keep actively playing audio-only music; stop every video.
     *
     * @return true when audio-only playback is allowed to continue in the background.
     */
    fun enforceBackgroundPlaybackPolicy(): Boolean {
        val player = mediaSession?.player ?: return false
        val currentItem = player.currentMediaItem
        val currentIsAudioOnly = currentItem?.mediaMetadata?.extras
            ?.getBoolean(PlaybackMetadata.AUDIO_ONLY, false) == true

        if (player.isPlaying && currentIsAudioOnly) {
            // A video must not begin after the user has left Rush.
            val firstQueuedIndex = (player.currentMediaItemIndex + 1).coerceAtLeast(0)
            for (index in player.mediaItemCount - 1 downTo firstQueuedIndex) {
                val queuedItem = player.getMediaItemAt(index)
                val queuedIsAudioOnly = queuedItem.mediaMetadata.extras
                    ?.getBoolean(PlaybackMetadata.AUDIO_ONLY, false) == true
                if (!queuedIsAudioOnly) player.removeMediaItem(index)
            }
            return true
        }

        // Video, paused media, and empty sessions do not stay alive in the background.
        pauseAllPlayersAndStopSelf()
        return false
    }

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        if (enforceBackgroundPlaybackPolicy()) {
            super.onTaskRemoved(rootIntent)
        }
    }

    override fun onDestroy() {
        mediaSession?.player?.let { player ->
            val mediaId = player.currentMediaItem?.mediaId.orEmpty()
            if (mediaId.isNotBlank()) {
                PlaybackProgress.save(this, mediaId, player.currentPosition, player.duration)
            }
        }
        serviceScope.cancel()
        sleepTimerRunnable?.let(sleepTimerHandler::removeCallbacks)
        sleepTimerRunnable = null
        sleepAfterCurrent = false
        mediaSession?.let { session ->
            session.player.release()
            session.release()
        }
        mediaSession = null
        streamCache?.release()
        streamCache = null
        if (activeInstance === this) activeInstance = null
        super.onDestroy()
    }
}
