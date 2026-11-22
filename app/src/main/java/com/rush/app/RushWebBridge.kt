package com.rush.app

import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class RushWebBridge(
    private val activity: MainActivity,
    private val webView: WebView
) {
    @Volatile private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private var captionListener: androidx.media3.common.Player.Listener? = null


    @JavascriptInterface
    fun getFeedPreferences(): String =
        FeedPreferences.asJson(activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)).toString()

    @JavascriptInterface
    fun saveFeedTopics(topicsJson: String) {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        runCatching { FeedPreferences.saveTopics(prefs, topicsJson) }
            .onSuccess { publish("rushFeedPreferences", FeedPreferences.asJson(prefs).toString()) }
            .onFailure { publish("rushFeedPreferencesError", JSONObject().put("message", it.message ?: "Could not save topics.").toString()) }
    }

    @JavascriptInterface
    fun followSource(kind: String, id: String, name: String, productive: Boolean) {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        runCatching { FeedPreferences.follow(prefs, kind, id, name, productive) }
            .onSuccess { publish("rushFeedPreferences", FeedPreferences.asJson(prefs).toString()) }
            .onFailure { publish("rushFeedPreferencesError", JSONObject().put("message", it.message ?: "Could not follow this source.").toString()) }
    }

    @JavascriptInterface
    fun unfollowSource(kind: String, id: String) {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        runCatching { FeedPreferences.unfollow(prefs, kind, id) }
            .onSuccess { publish("rushFeedPreferences", FeedPreferences.asJson(prefs).toString()) }
            .onFailure { publish("rushFeedPreferencesError", JSONObject().put("message", it.message ?: "Could not unfollow this source.").toString()) }
    }

    @JavascriptInterface
    fun setChannelRecommended(id: String, name: String, enabled: Boolean) {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        runCatching {
            require(id.isNotBlank() && id.length <= 256) { "Invalid channel ID." }
            require(name.isNotBlank() && name.length <= 160) { "Invalid channel name." }
            FeedPreferences.setProductive(prefs, id, name, enabled)
        }
            .onSuccess { publish("rushFeedPreferences", FeedPreferences.asJson(prefs).toString()) }
            .onFailure { publish("rushFeedPreferencesError", JSONObject().put("message", it.message ?: "Could not update this channel.").toString()) }
    }

    @JavascriptInterface
    fun searchChannels(query: String) {
        val term = query.trim().take(200)
        if (term.isEmpty()) return
        activity.lifecycleScope.launch {
            try {
                val channels = withContext(Dispatchers.IO) {
                    LiveYouTubeSearch.search(term, channels = true)
                        .filter { it.isChannel && it.channelId.isNotBlank() }
                        .distinctBy { it.channelId }
                        .take(20)
                }
                val payload = JSONArray().apply {
                    channels.forEach { channel ->
                        put(JSONObject()
                            .put("id", channel.channelId)
                            .put("name", channel.title)
                            .put("subscribers", channel.creator)
                            .put("description", channel.description)
                            .put("thumbnail", channel.thumbnailUrl))
                    }
                }
                publish("rushChannelSearchResults", payload.toString())
            } catch (error: Exception) {
                publish("rushChannelSearchError", JSONObject()
                    .put("message", playbackErrorMessage(error, "Channel search failed.")).toString())
            }
        }
    }

    @JavascriptInterface
    fun searchPlaylists(query: String) {
        val term = query.trim().take(200)
        if (term.isBlank()) return
        activity.lifecycleScope.launch {
            try {
                val playlists = withContext(Dispatchers.IO) {
                    LiveYouTubeSearch.search(term, playlists = true)
                        .filter { it.isCollection && it.collectionId.isNotBlank() }
                        .distinctBy { it.collectionId }
                        .take(30)
                }
                val payload = JSONArray().apply {
                    playlists.forEach { playlist ->
                        put(JSONObject()
                            .put("id", playlist.collectionId)
                            .put("title", playlist.title)
                            .put("creator", playlist.creator)
                            .put("videoCount", playlist.videoCount.ifBlank { playlist.duration })
                            .put("thumbnail", playlist.thumbnailUrl))
                    }
                }
                publish("rushPlaylistSearchResults", payload.toString())
            } catch (error: Exception) {
                publish("rushPlaylistSearchError", JSONObject()
                    .put("message", playbackErrorMessage(error, "Playlist search failed.")).toString())
            }
        }
    }

    @JavascriptInterface
    fun loadHomeFeed(mode: String) {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        activity.lifecycleScope.launch {
            val sources = FeedPreferences.sources(prefs)
            val topics = FeedPreferences.topics(prefs)
            val videos = LinkedHashMap<String, Pair<Int, JSONObject>>()
            val music = LinkedHashMap<String, JSONObject>()
            val news = LinkedHashMap<String, JSONObject>()
            val following = mode.equals("following", true)
            val selectedChannels = if (following) sources.filter { it.kind == "channel" }
                else sources.filter { it.kind == "channel" && it.productive }
            if (selectedChannels.isEmpty() && !following) {
                publish("rushHomeFeedResults", JSONObject()
                    .put("mode", "forYou").put("videos", JSONArray()).put("music", JSONArray())
                    .put("news", JSONArray()).put("message", "Choose productive YouTube channels in Search to start your For You feed.").toString())
                return@launch
            }
            try {
                withContext(Dispatchers.IO) {
                    selectedChannels.take(10).forEach { source ->
                        runCatching {
                            LiveYouTubeSearch.search(source.name, music = false)
                                .filter { it.videoId.isNotBlank() && (it.creator.equals(source.name, true) || it.creator.contains(source.name, true)) }
                                .forEach { item ->
                                    val score = FeedPreferences.rank(item.title, item.description, topics)
                                    val json = JSONObject().put("id", item.videoId).put("title", item.title)
                                        .put("artist", item.creator).put("duration", item.duration)
                                        .put("thumbnail", item.thumbnailUrl)
                                        .put("sourceId", source.id).put("sourceName", source.name)
                                    val previous = videos[item.videoId]
                                    if (previous == null || score > previous.first) videos[item.videoId] = score to json
                                }
                        }
                    }
                    if (following) {
                        sources.filter { it.kind == "artist" }.take(6).forEach { source ->
                            runCatching {
                                LiveYouTubeSearch.search(source.name, music = true).forEach { item ->
                                    if (item.videoId.isNotBlank()) music.putIfAbsent(item.videoId,
                                        JSONObject().put("id", item.videoId).put("title", item.title)
                                            .put("artist", item.creator).put("duration", item.duration)
                                            .put("thumbnail", item.thumbnailUrl))
                                }
                            }
                        }
                        sources.filter { it.kind == "news" }.take(6).forEach { source ->
                            runCatching {
                                val items = requestNews(source.name)
                                for (index in 0 until items.length()) {
                                    val article = items.optJSONObject(index) ?: continue
                                    val publisher = article.optString("source")
                                    if (publisher.contains(source.name, true) || source.name.contains(publisher, true)) {
                                        news.putIfAbsent(article.optString("url"), article)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) { }
            val rankedChannelGroups = videos.values.sortedByDescending { it.first }
                .groupBy { it.second.optString("sourceId") }
            val orderedVideos = buildList {
                var round = 0
                while (size < 30) {
                    var added = false
                    rankedChannelGroups.values.forEach { channelItems ->
                        val candidate = channelItems.getOrNull(round)
                        if (candidate != null && size < 30) {
                            add(candidate.second)
                            added = true
                        }
                    }
                    if (!added) break
                    round++
                }
            }
            publish("rushHomeFeedResults", JSONObject()
                .put("mode", if (following) "following" else "forYou")
                .put("videos", JSONArray(orderedVideos))
                .put("music", JSONArray(music.values.toList().take(20)))
                .put("news", JSONArray(news.values.toList().take(20)))
                .put("message", if (following && sources.isEmpty()) "Follow YouTube channels, news publishers or musicians in Search to build this feed."
                    else if (following && orderedVideos.isEmpty() && music.isEmpty() && news.isEmpty()) "No recent items were found from your followed sources."
                    else "").toString())
        }
    }

    @JavascriptInterface
    fun loadMusicSuggestions() {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        activity.lifecycleScope.launch {
            val follows = FeedPreferences.sources(prefs).filter { it.kind == "artist" }
            val history = runCatching { JSONArray(prefs.getString("music_history", "[]") ?: "[]") }
                .getOrDefault(JSONArray())
            val historyEntries = (0 until minOf(history.length(), 30))
                .mapNotNull { history.optJSONObject(it) }
            val playedIds = historyEntries.mapTo(mutableSetOf()) { it.optString("id") }
                .filter(String::isNotBlank).toSet()
            val artistWeights = LinkedHashMap<String, Int>()
            follows.forEach { artistWeights[it.name] = 75 }
            historyEntries.forEachIndexed { index, track ->
                val artist = track.optString("artist").trim()
                if (artist.isNotBlank()) {
                    artistWeights[artist] = (artistWeights[artist] ?: 0) + (50 - index).coerceAtLeast(5)
                }
            }

            if (artistWeights.isEmpty() && historyEntries.isEmpty()) {
                publish("rushMusicSuggestionResults", JSONObject().put("items", JSONArray())
                    .put("message", "Play a few tracks or follow musicians to build your listening profile.").toString())
                return@launch
            }

            val stopWords = setOf(
                "the", "and", "for", "with", "from", "this", "that", "your", "you", "official",
                "audio", "video", "lyrics", "lyric", "live", "music", "song", "songs", "feat",
                "ft", "remix", "version", "visualizer", "topic", "provided", "explicit"
            )
            val tokenWeights = LinkedHashMap<String, Int>()
            historyEntries.forEachIndexed { index, track ->
                val title = track.optString("title")
                val artist = track.optString("artist")
                val artistTokens = artist.lowercase(java.util.Locale.ROOT)
                    .split(Regex("[^\\p{L}\\p{N}]+")).filter(String::isNotBlank).toSet()
                title.lowercase(java.util.Locale.ROOT)
                    .split(Regex("[^\\p{L}\\p{N}]+"))
                    .filter { it.length >= 3 && it !in stopWords && it !in artistTokens }
                    .distinct()
                    .forEach { token ->
                        tokenWeights[token] = (tokenWeights[token] ?: 0) + (30 - index).coerceAtLeast(2)
                    }
            }

            val queries = LinkedHashMap<String, Int>()
            follows.take(4).forEach { queries[it.name] = 95 }
            artistWeights.entries.sortedByDescending { it.value }.take(5).forEach { (artist, weight) ->
                queries[artist] = maxOf(queries[artist] ?: 0, weight)
            }
            historyEntries.take(3).forEachIndexed { index, track ->
                val artist = track.optString("artist").trim()
                val title = track.optString("title").trim()
                if (artist.isNotBlank() && title.isNotBlank()) {
                    val seed = "$artist $title"
                    queries[seed] = maxOf(queries[seed] ?: 0, 65 - index * 8)
                }
            }
            tokenWeights.entries.sortedByDescending { it.value }.take(2).forEach { (token, weight) ->
                val query = "$token music"
                queries[query] = maxOf(queries[query] ?: 0, 20 + weight)
            }

            val suggestions = LinkedHashMap<String, Pair<Int, JSONObject>>()
            withContext(Dispatchers.IO) {
                queries.entries.take(8).forEach { (query, queryWeight) ->
                    runCatching {
                        LiveYouTubeSearch.search(query, music = true).forEach itemLoop@{ item ->
                            if (item.videoId.isBlank() || item.videoId in playedIds) return@itemLoop
                            val creator = item.creator.lowercase(java.util.Locale.ROOT)
                            val candidateText = (item.title + " " + item.creator).lowercase(java.util.Locale.ROOT)
                            val artistScore = artistWeights.entries.maxOfOrNull { (artist, weight) ->
                                val key = artist.lowercase(java.util.Locale.ROOT)
                                when {
                                    creator == key -> weight * 2
                                    creator.contains(key) || key.contains(creator) -> weight
                                    else -> 0
                                }
                            } ?: 0
                            val tasteTermsScore = tokenWeights.entries
                                .filter { (token, _) -> candidateText.contains(token) }
                                .sumOf { it.value }
                            val followedArtistBoost = if (follows.any {
                                creator.contains(it.name.lowercase(java.util.Locale.ROOT))
                            }) 80 else 0
                            val score = queryWeight + artistScore + tasteTermsScore + followedArtistBoost
                            val json = JSONObject().put("id", item.videoId).put("title", item.title)
                                .put("artist", item.creator).put("duration", item.duration)
                                .put("thumbnail", item.thumbnailUrl)
                            val old = suggestions[item.videoId]
                            if (old == null || score > old.first) suggestions[item.videoId] = score to json
                        }
                    }
                }
            }

            val sorted = suggestions.values.sortedByDescending { it.first }
            val artistCounts = LinkedHashMap<String, Int>()
            val selected = ArrayList<JSONObject>()
            sorted.forEach { candidate ->
                val artist = candidate.second.optString("artist")
                if ((artistCounts[artist] ?: 0) < 4 && selected.size < 24) {
                    selected += candidate.second
                    artistCounts[artist] = (artistCounts[artist] ?: 0) + 1
                }
            }
            if (selected.size < 12) {
                sorted.forEach { candidate ->
                    if (selected.size < 24 && selected.none { it.optString("id") == candidate.second.optString("id") }) {
                        selected += candidate.second
                    }
                }
            }
            publish("rushMusicSuggestionResults", JSONObject().put("items", JSONArray(selected))
                .put("message", if (selected.isEmpty()) "No suggestions found for your listening profile yet." else "").toString())
        }
    }

    @JavascriptInterface
    fun loadPersonalizedNews() {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        activity.lifecycleScope.launch {
            val topics = FeedPreferences.topics(prefs)
            val followedPublishers = FeedPreferences.sources(prefs).filter { it.kind == "news" }
            val queries = (topics.take(3) + followedPublishers.take(2).map { it.name }).distinct()
            val merged = LinkedHashMap<String, Pair<Int, JSONObject>>()
            withContext(Dispatchers.IO) {
                queries.forEach { query ->
                    runCatching {
                        val items = requestNews(query)
                        for (index in 0 until items.length()) {
                            val item = items.optJSONObject(index) ?: continue
                            val publisher = item.optString("source")
                            val followBoost = if (followedPublishers.any { publisher.contains(it.name, true) || it.name.contains(publisher, true) }) 18 else 0
                            val score = FeedPreferences.rank(item.optString("title"), item.optString("summary"), topics) + followBoost
                            val key = item.optString("url")
                            val previous = merged[key]
                            if (previous == null || score > previous.first) merged[key] = score to item
                        }
                    }
                }
            }
            val articles = merged.values.sortedByDescending { it.first }.take(40).map { it.second }
            publish("rushNewsResults", JSONArray(articles).toString())
        }
    }

    @JavascriptInterface
    fun loadCurrentPlayback() {
        activity.lifecycleScope.launch {
            runCatching {
                val controller = getController()
                val item = controller.currentMediaItem
                val metadata = item?.mediaMetadata
                val hasItem = item != null
                val audioOnly = metadata?.extras?.getBoolean(PlaybackMetadata.AUDIO_ONLY, false) == true
                val sourceId = metadata?.extras?.getString(PlaybackMetadata.SOURCE).orEmpty()
                publish("rushCurrentPlayback", JSONObject()
                    .put("hasItem", hasItem)
                    .put("isMusic", hasItem && audioOnly)
                    .put("isVideo", hasItem && !audioOnly)
                    .put("isPlaying", controller.isPlaying)
                    .put("title", metadata?.title?.toString().orEmpty())
                    .put("artist", metadata?.artist?.toString().orEmpty())
                    .put("artwork", metadata?.artworkUri?.toString().orEmpty())
                    .put("mediaId", item?.mediaId.orEmpty())
                    .put("sourceId", sourceId)
                    .put("isOffline", item?.localConfiguration?.uri?.scheme == "file")
                    .put("position", controller.currentPosition)
                    .put("duration", controller.duration).toString())
            }.onFailure {
                publish("rushCurrentPlayback", JSONObject().put("hasItem", false).put("isMusic", false)
                    .put("isVideo", false).put("isPlaying", false).toString())
            }
        }
    }

    @JavascriptInterface
    fun togglePlayback() {
        activity.lifecycleScope.launch {
            runCatching {
                val controller = getController()
                if (controller.isPlaying) controller.pause() else controller.play()
                loadCurrentPlayback()
            }
        }
    }

    @JavascriptInterface
    fun setVideoFullscreen(enabled: Boolean) {
        activity.setNativeVideoFullscreen(enabled)
    }

    @JavascriptInterface
    fun searchMusic(query: String) {
        val term = query.trim().take(200)
        if (term.isEmpty()) return
        activity.lifecycleScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    LiveYouTubeSearch.search(term, music = true).filter { it.videoId.isNotBlank() }.take(30)
                }
                val payload = JSONArray().apply {
                    results.forEach { item ->
                        put(JSONObject()
                            .put("id", item.videoId)
                            .put("title", item.title)
                            .put("artist", item.creator)
                            .put("duration", item.duration)
                            .put("thumbnail", thumbnailFor(item.videoId, item.thumbnailUrl)))
                    }
                }
                publish("rushMusicSearchResults", payload.toString())
            } catch (error: Exception) {
                publish("rushMusicSearchError", JSONObject()
                    .put("message", error.message ?: "Music search failed.").toString())
            }
        }
    }

    @JavascriptInterface
    fun playMusic(videoId: String, title: String, artist: String, thumbnail: String) {
        if (!videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) return
        activity.lifecycleScope.launch {
            publish("rushMusicPlaybackState", JSONObject().put("state", "loading").put("title", title).toString())
            try {
                val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
                val quality = prefs.getInt("audio_quality", 0).takeIf { it > 0 } ?: 160
                val resolved = withContext(Dispatchers.IO) {
                    resolvePlaybackMedia(videoId, audioOnly = true, maxAudioBitrate = quality)
                }
                val controller = getController()
                val metadata = MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .apply { if (thumbnail.isNotBlank()) setArtworkUri(android.net.Uri.parse(thumbnail)) }
                    .setExtras(PlaybackMetadata.extras(videoId, true, quality, false))
                    .build()
                val item = MediaItem.Builder()
                    .setMediaId(videoId)
                    .setUri(resolved.url)
                    .setMimeType(resolved.mimeType)
                    .setMediaMetadata(metadata)
                    .build()
                captionListener?.let { controller.removeListener(it) }
                val listener = object : androidx.media3.common.Player.Listener {
                    override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                        val lines = cueGroup.cues.mapNotNull { it.text?.toString()?.takeIf { it.isNotBlank() } }
                        publish("rushVideoCaptions", JSONArray(lines).toString())
                    }
                }
                captionListener = listener
                controller.addListener(listener)
                controller.setMediaItem(item)
                controller.prepare()
                controller.play()
                val history = runCatching { JSONArray(prefs.getString("music_history", "[]") ?: "[]") }.getOrDefault(JSONArray())
                val updatedHistory = JSONArray().put(JSONObject()
                    .put("id", videoId).put("title", title).put("artist", artist).put("thumbnail", thumbnail))
                for (index in 0 until history.length()) {
                    val entry = history.optJSONObject(index) ?: continue
                    if (entry.optString("id") != videoId && updatedHistory.length() < 50) updatedHistory.put(entry)
                }
                LocalLibrary.rememberPlayback(prefs, videoId, title, artist, true, thumbnail, videoId)
                prefs.edit()
                    .putString("music_last_id", videoId)
                    .putString("music_last_title", title)
                    .putString("music_last_artist", artist)
                    .putString("music_last_thumbnail", thumbnail)
                    .putString("music_history", updatedHistory.toString())
                    .apply()
                publish("rushMusicPlaybackState", JSONObject()
                    .put("state", "playing").put("id", videoId)
                    .put("title", title).put("artist", artist).put("thumbnail", thumbnail).toString())
            } catch (error: Exception) {
                publish("rushMusicPlaybackState", JSONObject()
                    .put("state", "error")
                    .put("message", error.message ?: "Could not play this track.").toString())
            }
        }
    }

    @JavascriptInterface
    fun queueMusic(videoId: String, title: String, artist: String, thumbnail: String) {
        if (!videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) return
        activity.lifecycleScope.launch {
            try {
                val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
                val quality = prefs.getInt("audio_quality", 0).takeIf { it > 0 } ?: 160
                val resolved = withContext(Dispatchers.IO) {
                    resolvePlaybackMedia(videoId, audioOnly = true, maxAudioBitrate = quality)
                }
                val metadata = MediaMetadata.Builder()
                    .setTitle(title).setArtist(artist)
                    .apply { if (thumbnail.isNotBlank()) setArtworkUri(android.net.Uri.parse(thumbnail)) }
                    .setExtras(PlaybackMetadata.extras(videoId, true, quality, false))
                    .build()
                val item = MediaItem.Builder().setMediaId(videoId).setUri(resolved.url)
                    .setMimeType(resolved.mimeType).setMediaMetadata(metadata).build()
                val controller = getController()
                controller.addMediaItem(item)
                if (controller.mediaItemCount == 1) {
                    controller.prepare()
                    controller.play()
                }
                publish("rushMusicQueued", JSONObject().put("title", title).toString())
            } catch (error: Exception) {
                publish("rushMusicSearchError", JSONObject().put("message", error.message ?: "Could not add this track to the queue.").toString())
            }
        }
    }

    @JavascriptInterface
    fun searchVideos(query: String) {
        val term = query.trim().take(200)
        if (term.isEmpty()) return
        activity.lifecycleScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    LiveYouTubeSearch.search(term, music = false).filter { it.videoId.isNotBlank() }.take(30)
                }
                val payload = JSONArray().apply {
                    results.forEach { item ->
                        put(JSONObject().put("id", item.videoId).put("title", item.title)
                            .put("artist", item.creator).put("duration", item.duration)
                            .put("thumbnail", thumbnailFor(item.videoId, item.thumbnailUrl)))
                    }
                }
                publish("rushVideoSearchResults", payload.toString())
            } catch (error: Exception) {
                publish("rushVideoSearchError", JSONObject()
                    .put("message", playbackErrorMessage(error, "Video search failed.")).toString())
            }
        }
    }

    @JavascriptInterface
    fun playVideo(videoId: String, title: String, creator: String, thumbnail: String) {
        if (!videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) return
        activity.lifecycleScope.launch {
            publish("rushVideoPlaybackState", JSONObject().put("state", "loading").put("title", title).toString())
            try {
                val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
                prefs.edit().remove("music_last_id").apply()
                val quality = prefs.getInt("video_quality", 0)
                val preferMp4 = prefs.getBoolean("download_mp4", false)
                val resolved = withContext(Dispatchers.IO) {
                    resolvePlaybackMedia(videoId, audioOnly = false, maxHeight = quality.takeIf { it > 0 },
                        preferMp4 = preferMp4, preferDash = !preferMp4, allowDashFallback = !preferMp4)
                }
                val controller = getController()
                val subtitleConfigs = resolved.subtitles.map { subtitle ->
                    MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitle.url))
                        .setMimeType(subtitle.mimeType).setLanguage(subtitle.languageTag)
                        .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                        .setLabel(subtitle.label).build()
                }
                val metadata = MediaMetadata.Builder().setTitle(title).setArtist(creator)
                    .apply { if (thumbnail.isNotBlank()) setArtworkUri(android.net.Uri.parse(thumbnail)) }
                    .setExtras(PlaybackMetadata.extras(videoId, false, quality, preferMp4)).build()
                val item = MediaItem.Builder().setMediaId(videoId).setUri(resolved.url)
                    .setMimeType(resolved.mimeType).setMediaMetadata(metadata)
                    .setSubtitleConfigurations(subtitleConfigs).build()
                controller.setMediaItem(item)
                controller.prepare()
                controller.play()
                LocalLibrary.rememberPlayback(prefs, videoId, title, creator, false, thumbnail, videoId)
                activity.runOnUiThread { activity.showNativeVideo(controller) }
                publish("rushVideoPlaybackState", JSONObject().put("state", "playing")
                    .put("title", title).put("creator", creator).put("captionTracks", subtitleConfigs.size).toString())
            } catch (error: Exception) {
                publish("rushVideoPlaybackState", JSONObject().put("state", "error")
                    .put("message", playbackErrorMessage(error, "Could not play this video.")).toString())
            }
        }
    }

    @JavascriptInterface
    fun showCurrentVideo() {
        activity.lifecycleScope.launch {
            runCatching {
                val controller = getController()
                val item = controller.currentMediaItem ?: return@launch
                val audioOnly = item.mediaMetadata.extras?.getBoolean(PlaybackMetadata.AUDIO_ONLY, false) == true
                if (!audioOnly) activity.runOnUiThread { activity.showNativeVideo(controller) }
            }
        }
    }

    @JavascriptInterface
    fun updateVideoPosition() {
        activity.updateNativeVideoPosition()
    }

    @JavascriptInterface
    fun hideVideo() {
        activity.hideNativeVideo()
    }

    @JavascriptInterface
    fun loadLyrics(title: String, artist: String) {
        val track = title.trim()
        val performer = artist.trim()
        if (track.isEmpty()) return
        activity.lifecycleScope.launch {
            publish("rushMusicLyricsLoading", JSONObject().toString())
            try {
                val lyrics = withContext(Dispatchers.IO) {
                    val url = okhttp3.HttpUrl.Builder().scheme("https").host("lrclib.net").addPathSegments("api/search")
                        .addQueryParameter("track_name", track)
                        .apply { if (performer.isNotBlank()) addQueryParameter("artist_name", performer) }
                        .build()
                    val request = okhttp3.Request.Builder().url(url).header("User-Agent", "Rush/1.0").build()
                    RushNetwork.newBuilder().callTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                        .build().newCall(request).execute().use { response ->
                            if (!response.isSuccessful) throw java.io.IOException("Lyrics are unavailable for this track.")
                            val results = JSONArray(response.body?.string().orEmpty())
                            var best: JSONObject? = null
                            for (i in 0 until results.length()) {
                                val candidate = results.optJSONObject(i) ?: continue
                                if (candidate.optString("syncedLyrics").isNotBlank()) { best = candidate; break }
                                if (best == null && candidate.optString("plainLyrics").isNotBlank()) best = candidate
                            }
                            best ?: throw java.io.IOException("No lyrics are available for this track.")
                        }
                }
                publish("rushMusicLyrics", JSONObject().put("synced", lyrics.optString("syncedLyrics"))
                    .put("plain", lyrics.optString("plainLyrics")).put("title", track).put("artist", performer).toString())
            } catch (error: Exception) {
                publish("rushMusicLyricsError", JSONObject().put("message", error.message ?: "Lyrics are unavailable.").toString())
            }
        }
    }

    @JavascriptInterface
    fun musicPosition(): Long = runCatching { controllerFuture?.get()?.currentPosition ?: 0L }.getOrDefault(0L)

    @JavascriptInterface
    fun loadCurrentMusicLyrics() {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        val id = prefs.getString("music_last_id", null)
        val title = prefs.getString("music_last_title", null).orEmpty()
        val artist = prefs.getString("music_last_artist", null).orEmpty()
        if (!id.isNullOrBlank() && title.isNotBlank()) loadLyrics(title, artist)
    }

    @JavascriptInterface
    fun loadMusicHistory() {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        publish("rushMusicHistory", prefs.getString("music_history", "[]") ?: "[]")
    }

    @JavascriptInterface
    fun loadNews(category: String) {
        val query = when (category.trim().lowercase()) {
            "world" -> "world news"
            "technology" -> "technology news"
            "science" -> "science news"
            "business" -> "business news"
            "health" -> "health news"
            "sports" -> "sports news"
            "entertainment" -> "entertainment news"
            "culture" -> "culture news"
            else -> "latest news"
        }
        fetchNews(query)
    }

    @JavascriptInterface
    fun searchNews(query: String) {
        val term = query.trim().take(200)
        if (term.isNotEmpty()) fetchNews(term)
    }

    @JavascriptInterface
    fun openNews(url: String) {
        val uri = runCatching { android.net.Uri.parse(url.trim().take(4096)) }.getOrNull() ?: return
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null) return
        activity.runOnUiThread {
            runCatching {
                activity.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
            }.onFailure {
                publish("rushNewsError", JSONObject().put("message", "No browser is available to open this article.").toString())
            }
        }
    }

    private fun fetchNews(query: String) {
        activity.lifecycleScope.launch {
            val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
            val cacheKey = "news_cache_" + query.trim().lowercase().replace(Regex("\\s+"), "_")
            val cached = prefs.getString(cacheKey, null)
            if (!cached.isNullOrBlank()) runCatching { JSONArray(cached) }.getOrNull()?.let { publish("rushNewsResults", it.toString()) }
            else publish("rushNewsLoading", JSONObject().put("query", query).toString())
            try {
                val articles = withContext(Dispatchers.IO) { requestNews(query) }
                prefs.edit().putString(cacheKey, articles.toString()).apply()
                publish("rushNewsResults", articles.toString())
            } catch (error: Exception) {
                if (cached.isNullOrBlank()) publish("rushNewsError", JSONObject().put("message", playbackErrorMessage(error, "Could not load news.")).toString())
            }
        }
    }
    private fun requestNews(query: String): JSONArray {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val request = okhttp3.Request.Builder()
            .url("https://news.google.com/rss/search?q=$encoded&hl=en&gl=US&ceid=US:en")
            .header("User-Agent", "Rush/1.0 (Android)")
            .build()
        val client = RushNetwork.newBuilder()
            .callTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("News feed failed (HTTP ${response.code}).")
            val parser = android.util.Xml.newPullParser()
            parser.setInput(response.body?.byteStream(), null)
            val output = JSONArray()
            var event = parser.eventType
            var insideItem = false
            var field = ""
            var title = ""
            var link = ""
            var date = ""
            var description = ""
            var source = ""
            var thumbnail = ""
            while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT && output.length() < 40) {
                when (event) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> {
                        if (parser.name == "item") {
                            insideItem = true
                            title = ""; link = ""; date = ""; description = ""; source = ""; thumbnail = ""
                        } else if (insideItem) {
                            field = parser.name
                            if (parser.name == "media:thumbnail" || parser.name == "media:content" || parser.name == "enclosure") {
                                thumbnail = parser.getAttributeValue(null, "url").orEmpty()
                            }
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.TEXT,
                    org.xmlpull.v1.XmlPullParser.CDSECT -> if (insideItem) {
                        val value = parser.text.orEmpty()
                        when (field) {
                            "title" -> title += value
                            "link" -> link += value
                            "pubDate" -> date += value
                            "description" -> description += value
                            "source" -> source += value
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.END_TAG -> {
                        if (parser.name == "item") {
                            if (thumbnail.isBlank()) {
                                thumbnail = description.substringAfter("src=\"", "").substringBefore("\"")
                                    .ifBlank { description.substringAfter("src='", "").substringBefore("'") }
                            }
                            if (thumbnail.startsWith("//")) thumbnail = "https:$thumbnail"
                            if (thumbnail.isBlank()) {
                                val host = runCatching { android.net.Uri.parse(link).host }.getOrNull().orEmpty()
                                if (host.isNotBlank()) thumbnail = "https://www.google.com/s2/favicons?domain=$host&sz=128"
                            }
                            val cleanSummary = description.replace(Regex("<[^>]+>"), " ")
                                .replace(Regex("\\s+"), " ").trim()
                            if (title.isNotBlank() && link.startsWith("https://")) {
                                output.put(JSONObject()
                                    .put("title", title.trim())
                                    .put("url", link.trim())
                                    .put("date", date.trim())
                                    .put("source", source.trim().ifBlank { "News source" })
                                    .put("summary", cleanSummary.take(280))
                                    .put("thumbnail", thumbnail.takeIf { it.startsWith("https://") }.orEmpty()))
                            }
                            insideItem = false
                        }
                        field = ""
                    }
                }
                event = parser.next()
            }
            return output
        }
    }

    @JavascriptInterface
    fun toggleMusicPlayback() {
        activity.lifecycleScope.launch {
            runCatching {
                val player = getController()
                if (player.isPlaying) player.pause() else player.play()
                publish("rushMusicPlaybackState", JSONObject()
                    .put("state", if (player.isPlaying) "playing" else "paused")
                    .put("title", player.mediaMetadata.title?.toString().orEmpty()).toString())
            }
        }
    }


    @JavascriptInterface
    fun browsePublicPlaylist(playlistId: String, playlistTitle: String) {
        val cleanId = playlistId.trim().take(128)
        if (!cleanId.matches(Regex("[A-Za-z0-9_-]{3,128}"))) {
            publish("rushPublicPlaylistError", JSONObject().put("message", "Invalid YouTube playlist ID.").toString())
            return
        }
        activity.lifecycleScope.launch {
            try {
                val videos = withContext(Dispatchers.IO) {
                    LiveYouTubeSearch.browseVideoPlaylist(cleanId)
                        .filter { it.videoId.matches(Regex("[A-Za-z0-9_-]{11}")) }
                        .take(100)
                }
                val payload = JSONArray().apply {
                    videos.forEach { video ->
                        put(JSONObject()
                            .put("id", video.videoId)
                            .put("title", video.title)
                            .put("creator", video.creator)
                            .put("duration", video.duration)
                            .put("thumbnail", video.thumbnailUrl))
                    }
                }
                publish("rushPublicPlaylistResults", JSONObject()
                    .put("id", cleanId)
                    .put("title", playlistTitle.trim().take(300).ifBlank { "YouTube playlist" })
                    .put("items", payload)
                    .put("count", videos.size).toString())
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                publish("rushPublicPlaylistError", JSONObject()
                    .put("message", playbackErrorMessage(error, "Could not open this playlist.")).toString())
            }
        }
    }

    @JavascriptInterface
    fun queuePublicPlaylist(playlistTitle: String, rawItems: String) {
        if (rawItems.length > 180_000) {
            publish("rushLibraryError", JSONObject().put("message", "This playlist is too large to queue at once.").toString())
            return
        }
        val array = runCatching { JSONArray(rawItems) }.getOrElse {
            publish("rushLibraryError", JSONObject().put("message", "Playlist data could not be read.").toString())
            return
        }
        val entries = buildList {
            for (index in 0 until minOf(array.length(), 50)) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                val title = item.optString("title").trim().take(300)
                if (!id.matches(Regex("[A-Za-z0-9_-]{11}")) || title.isBlank()) continue
                add(id to JSONObject()
                    .put("title", title)
                    .put("creator", item.optString("creator").trim().take(200))
                    .put("thumbnail", item.optString("thumbnail").take(2048)))
            }
        }.distinctBy { it.first }
        if (entries.isEmpty()) {
            publish("rushLibraryError", JSONObject().put("message", "This playlist contains no playable videos.").toString())
            return
        }
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        entries.forEach { (id, item) ->
            LocalLibrary.rememberItem(
                prefs,
                id,
                item.optString("title"),
                item.optString("creator"),
                false,
                item.optString("thumbnail"),
                id
            )
        }
        QueueStore.replace(prefs, entries.map { it.first })
        publish("rushLibraryData", LocalLibrary.load(prefs).toString())
        publish("rushPlaylistQueued", JSONObject()
            .put("title", playlistTitle.trim().take(300).ifBlank { "YouTube playlist" })
            .put("count", entries.size).toString())
    }

    @JavascriptInterface
    fun loadLibrary() {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        publish("rushLibraryData", LocalLibrary.load(prefs).toString())
    }
    
    @JavascriptInterface
    fun playLibraryItem(id: String) {
        val cleanId = id.trim().take(256)
        if (cleanId.isBlank()) return
        activity.lifecycleScope.launch {
            try {
                val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
                val entry = runCatching {
                    JSONObject(prefs.getString("item_metadata", "{}") ?: "{}").optJSONObject(cleanId)
                }.getOrNull() ?: throw java.io.IOException("This item is no longer in your library.")
                val localPath = entry.optString("localPath")
                val downloaded = if (localPath.isNotBlank()) withContext(Dispatchers.IO) {
                    OfflineDownloads.list(activity.applicationContext)
                        .firstOrNull { it.complete && it.file.absolutePath == localPath }
                } else null
                if (downloaded != null) {
                    playDownloaded(downloaded.id)
                    return@launch
                }
                val sourceId = entry.optString("sourceId").ifBlank { cleanId }
                val title = entry.optString("title").take(300)
                val creator = entry.optString("creator").take(200)
                val thumbnail = entry.optString("thumbnail").take(2048)
                if (entry.optBoolean("music")) playMusic(sourceId, title, creator, thumbnail)
                else playVideo(sourceId, title, creator, thumbnail)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                publish("rushLibraryError", JSONObject().put("message", playbackErrorMessage(error, "Could not play this library item.")).toString())
            }
        }
    }

    @JavascriptInterface
    fun saveLibraryItem(id: String, title: String, creator: String, isMusic: Boolean, thumbnail: String) {
        val cleanId = id.trim().take(256)
        if (cleanId.isBlank()) return
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        runCatching {
            LocalLibrary.save(prefs, cleanId, title, creator, isMusic, thumbnail, cleanId)
        }.onSuccess {
            publish("rushLibraryData", LocalLibrary.load(prefs).toString())
        }.onFailure {
            publish("rushLibraryError", JSONObject().put("message", it.message ?: "Could not save this item.").toString())
        }
    }

    @JavascriptInterface
    fun removeSavedLibraryItem(id: String) {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        LocalLibrary.unsave(prefs, id)
        publish("rushLibraryData", LocalLibrary.load(prefs).toString())
    }

    @JavascriptInterface
    fun removeHistoryItem(id: String) {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        LocalLibrary.removeFromHistory(prefs, id)
        publish("rushLibraryData", LocalLibrary.load(prefs).toString())
    }

    @JavascriptInterface
    fun clearPlaybackHistory() {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        LocalLibrary.clearHistory(prefs)
        publish("rushLibraryData", LocalLibrary.load(prefs).toString())
    }

    @JavascriptInterface
    fun queueLibraryItem(id: String, title: String, creator: String, isMusic: Boolean, thumbnail: String) {
        val cleanId = id.trim().take(256)
        if (cleanId.isBlank()) return
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        LocalLibrary.rememberItem(prefs, cleanId, title, creator, isMusic, thumbnail, cleanId)
        val queue = QueueStore.add(prefs, cleanId)
        publish("rushLibraryData", LocalLibrary.load(prefs).toString())
        publish("rushQueueUpdated", JSONObject().put("size", queue.size).toString())
    }

    @JavascriptInterface
    fun removeQueueItem(index: Int) {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        if (index in 0..499) QueueStore.removeAt(prefs, index)
        publish("rushLibraryData", LocalLibrary.load(prefs).toString())
    }

    @JavascriptInterface
    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        if (fromIndex in 0..499 && toIndex in 0..499) QueueStore.move(prefs, fromIndex, toIndex)
        publish("rushLibraryData", LocalLibrary.load(prefs).toString())
    }

    @JavascriptInterface
    fun clearPlaybackQueue() {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        QueueStore.clear(prefs)
        publish("rushLibraryData", LocalLibrary.load(prefs).toString())
    }

    @JavascriptInterface
    fun createLibraryPlaylist(name: String) {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        runCatching { PlaylistStore.create(prefs, name.take(80)) }
            .onSuccess { publish("rushLibraryData", LocalLibrary.load(prefs).toString()) }
            .onFailure { publish("rushLibraryError", JSONObject().put("message", it.message ?: "Could not create the playlist.").toString()) }
    }

    @JavascriptInterface
    fun addLibraryItemToPlaylist(
        playlistId: String,
        id: String,
        title: String,
        creator: String,
        isMusic: Boolean,
        thumbnail: String
    ) {
        val cleanId = id.trim().take(256)
        if (playlistId.isBlank() || cleanId.isBlank()) return
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        runCatching {
            LocalLibrary.rememberItem(prefs, cleanId, title, creator, isMusic, thumbnail, cleanId)
            PlaylistStore.add(prefs, playlistId.take(64), cleanId)
        }.onSuccess {
            publish("rushLibraryData", LocalLibrary.load(prefs).toString())
        }.onFailure {
            publish("rushLibraryError", JSONObject().put("message", it.message ?: "Could not add this item.").toString())
        }
    }

    @JavascriptInterface
    fun removeLibraryItemFromPlaylist(playlistId: String, id: String) {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        PlaylistStore.remove(prefs, playlistId.take(64), id.trim().take(256))
        publish("rushLibraryData", LocalLibrary.load(prefs).toString())
    }

    @JavascriptInterface
    fun deleteLibraryPlaylist(playlistId: String) {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        PlaylistStore.delete(prefs, playlistId.take(64))
        publish("rushLibraryData", LocalLibrary.load(prefs).toString())
    }

    @JavascriptInterface
    fun playLibraryPlaylist(playlistId: String) {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        val playlist = PlaylistStore.load(prefs).firstOrNull { it.id == playlistId.take(64) }
        if (playlist == null || playlist.itemIds.isEmpty()) {
            publish("rushLibraryError", JSONObject().put("message", "This playlist is empty or unavailable.").toString())
            return
        }
        QueueStore.replace(prefs, playlist.itemIds)
        publish("rushLibraryData", LocalLibrary.load(prefs).toString())
        playLibraryQueue()
    }

    @JavascriptInterface
    fun playLibraryQueue() {
        activity.lifecycleScope.launch {
            val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
            val ids = QueueStore.load(prefs).take(50)
            if (ids.isEmpty()) {
                publish("rushLibraryError", JSONObject().put("message", "Your queue is empty.").toString())
                return@launch
            }
            try {
                val metadata = runCatching {
                    JSONObject(prefs.getString("item_metadata", "{}") ?: "{}")
                }.getOrDefault(JSONObject())
                val queue = ArrayList<MediaItem>()
                var skipped = 0
                for (id in ids) {
                    val entry = metadata.optJSONObject(id)
                    if (entry == null) {
                        skipped++
                        continue
                    }
                    val title = entry.optString("title").take(300)
                    val creator = entry.optString("creator").take(200)
                    val isMusic = entry.optBoolean("music")
                    val localPath = entry.optString("localPath")
                    val localFile = java.io.File(localPath)
                    val sourceId = entry.optString("sourceId").ifBlank { id }
                    val thumbnail = entry.optString("thumbnail")
                    val resolved = if (localFile.isFile) null else withContext(Dispatchers.IO) {
                        resolvePlaybackMedia(
                            sourceId,
                            audioOnly = isMusic,
                            maxHeight = if (isMusic) null else prefs.getInt("video_quality", 0).takeIf { it > 0 },
                            maxAudioBitrate = if (isMusic) prefs.getInt("audio_quality", 160).takeIf { it > 0 } else null,
                            preferMp4 = !isMusic && prefs.getBoolean("download_mp4", false),
                            preferDash = !isMusic && !prefs.getBoolean("download_mp4", false),
                            allowDashFallback = !isMusic && !prefs.getBoolean("download_mp4", false)
                        )
                    }
                    val uri = if (localFile.isFile) {
                        android.net.Uri.fromFile(localFile)
                    } else {
                        android.net.Uri.parse(
                            resolved?.url ?: throw java.io.IOException("Could not resolve this queued item.")
                        )
                    }
                    val subtitleConfigs = resolved?.subtitles.orEmpty().map { subtitle ->
                        MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitle.url))
                            .setMimeType(subtitle.mimeType)
                            .setLanguage(subtitle.languageTag)
                            .setLabel(subtitle.label)
                            .build()
                    }
                    val itemMetadata = MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(creator)
                        .apply { if (thumbnail.startsWith("https://")) setArtworkUri(android.net.Uri.parse(thumbnail)) }
                        .setExtras(PlaybackMetadata.extras(sourceId, isMusic, 0, false))
                        .build()
                    queue += MediaItem.Builder()
                        .setMediaId(id)
                        .setUri(uri)
                        .apply { resolved?.mimeType?.let { setMimeType(it) } }
                        .setMediaMetadata(itemMetadata)
                        .setSubtitleConfigurations(subtitleConfigs)
                        .build()
                }
                if (queue.isEmpty()) throw java.io.IOException("No items in this queue could be prepared.")
                val controller = getController()
                controller.setMediaItems(queue)
                controller.prepare()
                controller.play()
                queue.firstOrNull()?.let { first ->
                    metadata.optJSONObject(first.mediaId)?.let { entry ->
                        LocalLibrary.rememberPlayback(
                            prefs, first.mediaId, entry.optString("title"), entry.optString("creator"),
                            entry.optBoolean("music"), entry.optString("thumbnail"),
                            entry.optString("sourceId").ifBlank { first.mediaId },
                            entry.optString("localPath")
                        )
                    }
                }
                if (controller.currentMediaItem?.mediaMetadata?.extras?.getBoolean(PlaybackMetadata.AUDIO_ONLY, false) != true) {
                    activity.runOnUiThread { activity.showNativeVideo(controller) }
                } else {
                    activity.hideNativeVideo()
                }
                publish("rushQueuePlayback", JSONObject()
                    .put("state", "playing")
                    .put("count", queue.size)
                    .put("skipped", skipped).toString())
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                publish("rushQueuePlayback", JSONObject()
                    .put("state", "error")
                    .put("message", playbackErrorMessage(error, "Could not play this queue.")).toString())
            }
        }
    }

    @JavascriptInterface
    fun downloadMedia(
        id: String,
        title: String,
        creator: String,
        isAudio: Boolean,
        transcodeToMp3: Boolean
    ) {
        val sourceId = id.trim().take(256)
        if (!sourceId.matches(Regex("[A-Za-z0-9_-]{11}"))) return
        if (transcodeToMp3 && !isAudio) return
        activity.requestDownloadNotificationPermission()
        activity.lifecycleScope.launch {
            publish("rushDownloadAction", JSONObject().put("state", "resolving").put("title", title.take(300)).toString())
            try {
                val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
                val maxHeight = if (isAudio) null else prefs.getInt("video_quality", 0).takeIf { it > 0 }
                val maxAudioBitrate = if (isAudio) prefs.getInt("audio_quality", 160).takeIf { it > 0 } else null
                val preferMp4 = !isAudio && prefs.getBoolean("download_mp4", false)
                val resolved = withContext(Dispatchers.IO) {
                    resolvePlaybackMedia(
                        sourceId,
                        audioOnly = isAudio,
                        maxHeight = maxHeight,
                        maxAudioBitrate = maxAudioBitrate,
                        preferMp4 = preferMp4,
                        preferDash = !isAudio && !preferMp4,
                        allowDashFallback = !isAudio && !preferMp4
                    )
                }
                val workId = withContext(Dispatchers.IO) {
                    OfflineDownloads.enqueue(
                        activity.applicationContext,
                        sourceId,
                        title.take(300),
                        creator.take(200),
                        isAudio,
                        resolved,
                        maxHeight,
                        maxAudioBitrate,
                        transcodeToMp3,
                        preferMp4
                    )
                }
                publish("rushDownloadAction", JSONObject()
                    .put("state", "queued")
                    .put("title", title.take(300))
                    .put("workId", workId.toString()).toString())
                loadDownloads()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                publish("rushDownloadAction", JSONObject()
                    .put("state", "error")
                    .put("message", playbackErrorMessage(error, "Could not start this download.")).toString())
            }
        }
    }

    @JavascriptInterface
    fun loadDownloads() {
        activity.lifecycleScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    val downloads = OfflineDownloads.list(activity.applicationContext)
                    val works = runCatching { OfflineDownloads.workInfos(activity.applicationContext) }.getOrDefault(emptyList())
                    val bytes = OfflineDownloads.storageBytes(activity.applicationContext)
                    Triple(downloads, works, bytes)
                }
                val workById = snapshot.second.mapNotNull { work ->
                    val id = work.tags.firstOrNull { it.startsWith(OfflineDownloads.WORK_TAG + ":") }
                        ?.substringAfter(":")
                    id?.let { it to work }
                }.toMap()
                val array = JSONArray().apply {
                    snapshot.first.forEach { media ->
                        val work = workById[media.id]
                        put(JSONObject()
                            .put("id", media.id)
                            .put("sourceId", media.sourceId)
                            .put("title", media.title)
                            .put("creator", media.creator)
                            .put("isAudio", media.isAudio)
                            .put("extension", media.extension)
                            .put("quality", media.quality)
                            .put("complete", media.complete)
                            .put("size", media.file.length())
                            .put("progress", work?.progress?.getInt("progress", 0) ?: if (media.complete) 100 else 0)
                            .put("stage", work?.progress?.getString("stage").orEmpty())
                            .put("state", work?.state?.name ?: if (media.complete) "SUCCEEDED" else "NONE")
                        )
                    }
                }
                publish("rushDownloadsData", JSONObject()
                    .put("items", array)
                    .put("count", snapshot.first.size)
                    .put("bytes", snapshot.third).toString())
            } catch (error: Exception) {
                publish("rushDownloadsError", JSONObject().put("message", error.message ?: "Could not load offline downloads.").toString())
            }
        }
    }

    @JavascriptInterface
    fun cancelDownload(id: String) {
        val cleanId = id.trim().take(64)
        if (!cleanId.matches(Regex("[A-Fa-f0-9-]{16,64}"))) return
        OfflineDownloads.cancel(activity.applicationContext, cleanId)
        loadDownloads()
    }

    @JavascriptInterface
    fun resumeDownload(id: String) {
        val cleanId = id.trim().take(64)
        if (cleanId.isBlank()) return
        activity.lifecycleScope.launch {
            try {
                val media = withContext(Dispatchers.IO) {
                    OfflineDownloads.list(activity.applicationContext).firstOrNull { it.id == cleanId }
                } ?: throw java.io.IOException("This download could not be found.")
                OfflineDownloads.resume(activity.applicationContext, media)
                publish("rushDownloadAction", JSONObject().put("state", "queued").put("title", media.title).toString())
                loadDownloads()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                publish("rushDownloadsError", JSONObject().put("message", error.message ?: "Could not resume this download.").toString())
            }
        }
    }

    @JavascriptInterface
    fun deleteDownload(id: String) {
        val cleanId = id.trim().take(64)
        if (cleanId.isBlank()) return
        activity.lifecycleScope.launch {
            try {
                val media = withContext(Dispatchers.IO) {
                    OfflineDownloads.list(activity.applicationContext).firstOrNull { it.id == cleanId }
                } ?: return@launch
                OfflineDownloads.delete(activity.applicationContext, media)
                loadDownloads()
            } catch (error: Exception) {
                publish("rushDownloadsError", JSONObject().put("message", error.message ?: "Could not delete this download.").toString())
            }
        }
    }

    @JavascriptInterface
    fun playDownloaded(id: String) {
        val cleanId = id.trim().take(64)
        if (cleanId.isBlank()) return
        activity.lifecycleScope.launch {
            try {
                val media = withContext(Dispatchers.IO) {
                    OfflineDownloads.list(activity.applicationContext).firstOrNull { it.id == cleanId && it.complete }
                } ?: throw java.io.IOException("This file is not available offline.")
                val controller = getController()
                val mime = when (media.extension.lowercase()) {
                    "mp3" -> "audio/mpeg"
                    "m4a", "mp4" -> if (media.isAudio) "audio/mp4" else "video/mp4"
                    "webm" -> if (media.isAudio) "audio/webm" else "video/webm"
                    else -> if (media.isAudio) "audio/*" else "video/*"
                }
                val metadata = MediaMetadata.Builder()
                    .setTitle(media.title)
                    .setArtist(media.creator)
                    .setExtras(PlaybackMetadata.extras(media.sourceId.ifBlank { media.id }, media.isAudio, 0, false))
                    .build()
                val item = MediaItem.Builder()
                    .setMediaId("offline:${media.id}")
                    .setUri(android.net.Uri.fromFile(media.file))
                    .setMimeType(mime)
                    .setMediaMetadata(metadata)
                    .build()
                controller.setMediaItem(item)
                controller.prepare()
                controller.play()
                val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
                val sourceId = media.sourceId.ifBlank { media.id }
                LocalLibrary.rememberPlayback(
                    prefs, sourceId, media.title, media.creator, media.isAudio, "", sourceId,
                    media.file.absolutePath
                )
                if (media.isAudio) activity.hideNativeVideo()
                else activity.runOnUiThread { activity.showNativeVideo(controller) }
                publish("rushCurrentPlayback", JSONObject()
                    .put("hasItem", true)
                    .put("isMusic", media.isAudio)
                    .put("isVideo", !media.isAudio)
                    .put("isPlaying", true)
                    .put("title", media.title)
                    .put("artist", media.creator)
                    .put("artwork", "")
                    .put("mediaId", "offline:${media.id}")
                    .put("sourceId", media.sourceId)
                    .put("isOffline", true).toString())
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                publish("rushDownloadAction", JSONObject().put("state", "error")
                    .put("message", error.message ?: "Could not play this download.").toString())
            }
        }
    }

    private suspend fun getController(): MediaController {
        val future = withContext(Dispatchers.Main) {
            controllerFuture ?: run {
                val token = SessionToken(activity, android.content.ComponentName(activity, RushPlaybackService::class.java))
                MediaController.Builder(activity, token).buildAsync().also { controllerFuture = it }
            }
        }
        return withContext(Dispatchers.IO) { future.get() }
    }

    fun release() {
        controllerFuture?.let { future ->
            if (future.isDone) runCatching { future.get().let { player -> captionListener?.let(player::removeListener) } }
            MediaController.releaseFuture(future)
        }
        captionListener = null
        controllerFuture = null
    }

    private fun thumbnailFor(videoId: String, candidate: String): String =
        candidate.takeIf { it.startsWith("https://") }
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

    private fun publish(callback: String, json: String) {
        webView.post {
            val safe = JSONObject.quote(json)
            webView.evaluateJavascript("window.$callback && window.$callback(JSON.parse($safe))", null)
        }
    }
}
