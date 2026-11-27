package com.rush.app

import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject

private const val HOME_FEED_CACHE_TTL_MS = 3 * 60 * 1000L
private const val NEWS_THUMBNAIL_CACHE_KEY = "rush_news_thumbnail_cache_v1"

private val NEWS_RSS_CLIENT by lazy {
    RushNetwork.newBuilder()
        .connectTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(9, java.util.concurrent.TimeUnit.SECONDS)
        .build()
}

private val NEWS_ARTICLE_CLIENT by lazy {
    RushNetwork.newBuilder()
        .connectTimeout(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
        .readTimeout(2500, java.util.concurrent.TimeUnit.MILLISECONDS)
        .callTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
        .build()
}

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
    fun addFeedTopic(topic: String) {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        runCatching { FeedPreferences.addTopic(prefs, topic) }
            .onSuccess { publish("rushFeedPreferences", FeedPreferences.asJson(prefs).toString()) }
            .onFailure { publish("rushFeedPreferencesError", JSONObject().put("message", it.message ?: "Could not add that topic.").toString()) }
    }

    @JavascriptInterface
    fun removeFeedTopic(topic: String) {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        runCatching { FeedPreferences.removeTopic(prefs, topic) }
            .onSuccess { publish("rushFeedPreferences", FeedPreferences.asJson(prefs).toString()) }
            .onFailure { publish("rushFeedPreferencesError", JSONObject().put("message", it.message ?: "Could not remove that topic.").toString()) }
    }

    @JavascriptInterface
    fun setTopicPreference(topic: String, mode: String) {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        runCatching { FeedPreferences.setTopicMode(prefs, topic, mode) }
            .onSuccess { publish("rushFeedPreferences", FeedPreferences.asJson(prefs).toString()) }
            .onFailure { publish("rushFeedPreferencesError", JSONObject().put("message", it.message ?: "Could not update that topic.").toString()) }
    }

    @JavascriptInterface
    fun snoozeFeedTopic(topic: String) {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        runCatching { FeedPreferences.snoozeTopic(prefs, topic) }
            .onSuccess { publish("rushFeedPreferences", FeedPreferences.asJson(prefs).toString()) }
            .onFailure { publish("rushFeedPreferencesError", JSONObject().put("message", it.message ?: "Could not snooze that topic.").toString()) }
    }

    @JavascriptInterface
    fun unsnoozeFeedTopic(topic: String) {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        runCatching { FeedPreferences.unsnoozeTopic(prefs, topic) }
            .onSuccess { publish("rushFeedPreferences", FeedPreferences.asJson(prefs).toString()) }
            .onFailure { publish("rushFeedPreferencesError", JSONObject().put("message", it.message ?: "Could not restore that topic.").toString()) }
    }

    @JavascriptInterface
    fun getAutoplayEnabled(): Boolean =
        activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
            .getBoolean("autoplay_video", true)

    @JavascriptInterface
    fun getVideoRating(id: String): Int {
        val cleanId = id.trim().take(256)
        if (cleanId.isBlank()) return 0
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        return runCatching {
            JSONObject(prefs.getString("rush_video_feedback", "{}") ?: "{}")
                .optJSONObject(cleanId)?.optInt("rating", 0) ?: 0
        }.getOrDefault(0)
    }

    @JavascriptInterface
    fun rateVideo(id: String, title: String, creator: String, rating: Int) {
        val cleanId = id.trim().take(256)
        if (cleanId.isBlank() || rating !in setOf(-1, 0, 1)) return
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        val feedback = runCatching { JSONObject(prefs.getString("rush_video_feedback", "{}") ?: "{}") }
            .getOrDefault(JSONObject())
        feedback.put(cleanId, JSONObject()
            .put("rating", rating)
            .put("title", title.take(300))
            .put("creator", creator.take(200))
            .put("updatedAt", System.currentTimeMillis()))
        if (rating == 1) LocalLibrary.like(prefs, cleanId, title, creator, "")
        else LocalLibrary.unlike(prefs, cleanId)
        while (feedback.length() > 300) {
            val oldest = feedback.keys().asSequence().minByOrNull { feedback.optJSONObject(it)?.optLong("updatedAt", 0L) ?: 0L } ?: break
            feedback.remove(oldest)
        }
        prefs.edit().putString("rush_video_feedback", feedback.toString()).apply()
        publish("rushVideoRatingState", JSONObject().put("id", cleanId).put("rating", rating).toString())
    }

    @JavascriptInterface
    fun setAutoplayEnabled(enabled: Boolean) {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("autoplay_video", enabled).apply()
        activity.runOnUiThread { RushPlaybackService.activeInstance?.setAutoplayEnabled(enabled) }
        publish("rushAutoplayState", JSONObject().put("enabled", enabled).toString())
    }

    @JavascriptInterface
    fun setSleepTimer(minutes: Int) {
        runCatching {
            RushPlaybackService.activeInstance?.setSleepTimer(minutes)
                ?: throw IllegalStateException("Start playback before setting a sleep timer.")
        }.onSuccess { message ->
            publish("rushSleepTimerState", JSONObject().put("message", message).put("minutes", minutes).toString())
        }.onFailure { error ->
            publish("rushSleepTimerState", JSONObject().put("message", error.message ?: "Could not set sleep timer.").put("error", true).toString())
        }
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
                    val raw = LiveYouTubeSearch.search(term, channels = true)
                        .filter { it.isChannel && it.channelId.isNotBlank() }
                        .distinctBy { it.channelId }
                    rankYouTubeSearch(term, raw, RushDiscoveryEngine.Kind.CHANNEL).take(20)
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
                    val raw = LiveYouTubeSearch.search(term, playlists = true)
                        .filter { it.isCollection && it.collectionId.isNotBlank() }
                        .distinctBy { it.collectionId }
                    rankYouTubeSearch(term, raw, RushDiscoveryEngine.Kind.PLAYLIST).take(30)
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
            val profile = discoveryProfile(prefs)
            val following = mode.equals("following", true)
            val feedMode = if (following) "following" else "forYou"
            val cacheSignature = profile.topics.joinToString("|") + "|" +
                profile.showMoreTopics.sorted().joinToString("|") + "|" +
                sources.joinToString("|") { "${it.kind}:${it.id}:${it.name}" } + "|" +
                prefs.getString("rush_video_feedback", "{}")
            val cacheKey = "rush_home_feed_v5_${feedMode}_${Integer.toHexString(cacheSignature.hashCode())}"
            val cached = prefs.getString(cacheKey, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
            val cachedAt = cached?.optLong("fetchedAt", 0L) ?: 0L
            if (cached != null && cachedAt > 0L &&
                System.currentTimeMillis() - cachedAt in 0..HOME_FEED_CACHE_TTL_MS) {
                publish("rushHomeFeedResults", cached.toString())
                return@launch
            }
            publish("rushHomeFeedLoading", JSONObject().put("mode", feedMode).toString())

            val channelSources = if (following) FeedPreferences.forYouChannels(sources)
                else FeedPreferences.forYouChannels(sources).take(4)
            val artistSources = sources.filter { it.kind == "artist" }.take(4)
            val topicQueries = if (following) emptyList() else profile.topics.take(8).mapIndexed { index, topic ->
                when (index % 3) {
                    0 -> "$topic explained"
                    1 -> "$topic tutorial"
                    else -> "$topic documentary"
                }
            }
            val videoQueries = channelSources.map { it.name to it } +
                topicQueries.map { it to null }

            val videoHits = LinkedHashMap<String, RushDiscoveryEngine.FeedHit>()
            val videoPayloads = LinkedHashMap<String, JSONObject>()
            val musicHits = LinkedHashMap<String, RushDiscoveryEngine.FeedHit>()
            val musicPayloads = LinkedHashMap<String, JSONObject>()

            try {
                val fetched = withContext(Dispatchers.IO) {
                    coroutineScope {
                        val slots = Semaphore(5)
                        val videoJobs = videoQueries.map { (query, source) ->
                            async {
                                Triple(query, source, runCatching {
                                    slots.withPermit { LiveYouTubeSearch.search(query, music = false) }
                                }.getOrDefault(emptyList()))
                            }
                        }
                        val musicQueries = (artistSources.map { it.name } +
                            if (!following && profile.topics.any {
                                it.equals("Music", true) || it.equals("Music production", true)
                            }) listOf("music production explained", "music theory lesson") else emptyList())
                            .distinct().take(5)
                        val musicJobs = musicQueries.map { query ->
                            async {
                                query to runCatching {
                                    slots.withPermit { LiveYouTubeSearch.search(query, music = true) }
                                }.getOrDefault(emptyList())
                            }
                        }
                        Pair(videoJobs.awaitAll(), musicJobs.awaitAll())
                    }
                }

                fetched.first.forEach { (_, source, results) ->
                    results.filter { item ->
                        item.videoId.isNotBlank() && (!following || source == null ||
                            item.creator.equals(source.name, true) || item.creator.contains(source.name, true))
                    }.forEach { item ->
                        val body = item.title + " " + item.description
                        val candidate = RushDiscoveryEngine.Candidate(
                            id = item.videoId,
                            kind = RushDiscoveryEngine.Kind.VIDEO,
                            title = item.title,
                            creator = item.creator,
                            summary = item.description,
                            sourceFollowed = source != null,
                            durationSeconds = parseDurationSeconds(item.duration),
                            topicTags = profile.topics.filter { topic -> body.contains(topic, true) }.toSet()
                        )
                        val json = JSONObject()
                            .put("id", item.videoId)
                            .put("title", item.title)
                            .put("artist", item.creator)
                            .put("duration", item.duration)
                            .put("thumbnail", thumbnailFor(item.videoId, item.thumbnailUrl))
                            .put("sourceId", source?.id ?: "")
                            .put("sourceName", source?.name ?: "")
                        val score = RushDiscoveryEngine.scoreFeed(candidate, profile)
                        val previous = videoHits[item.videoId]
                        if (previous == null || score > previous.score) {
                            videoHits[item.videoId] = RushDiscoveryEngine.FeedHit(candidate, score)
                            videoPayloads[item.videoId] = json
                        }
                    }
                }

                fetched.second.forEach { (_, results) ->
                    results.filter { it.videoId.isNotBlank() }.forEach { item ->
                        val candidate = RushDiscoveryEngine.Candidate(
                            id = item.videoId,
                            kind = RushDiscoveryEngine.Kind.MUSIC,
                            title = item.title,
                            creator = item.creator,
                            sourceFollowed = artistSources.any { item.creator.contains(it.name, true) },
                            durationSeconds = parseDurationSeconds(item.duration)
                        )
                        val json = JSONObject()
                            .put("id", item.videoId)
                            .put("title", item.title)
                            .put("artist", item.creator)
                            .put("duration", item.duration)
                            .put("thumbnail", thumbnailFor(item.videoId, item.thumbnailUrl))
                        val score = RushDiscoveryEngine.scoreFeed(candidate, profile)
                        val previous = musicHits[item.videoId]
                        if (previous == null || score > previous.score) {
                            musicHits[item.videoId] = RushDiscoveryEngine.FeedHit(candidate, score)
                            musicPayloads[item.videoId] = json
                        }
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("RushFeed", "Recommendation refresh failed", error)
            }

            val videos = RushDiscoveryEngine.diversifyFeed(videoHits.values.toList(), 30, 2)
                .mapNotNull { videoPayloads[it.candidate.id] }
            val music = RushDiscoveryEngine.diversifyFeed(musicHits.values.toList(), 12, 3)
                .mapNotNull { musicPayloads[it.candidate.id] }
            val payload = JSONObject()
                .put("mode", feedMode)
                .put("videos", JSONArray(videos))
                .put("music", JSONArray(music))
                .put("news", JSONArray())
                .put("fetchedAt", System.currentTimeMillis())
                .put("message", when {
                    following && channelSources.isEmpty() ->
                        "Follow YouTube channels to build your subscriptions feed."
                    following && videos.isEmpty() && music.isEmpty() ->
                        "No recent uploads were found from your followed channels."
                    !following && videos.isEmpty() && music.isEmpty() ->
                        "No recommendations are available right now. Refresh or add a topic."
                    else -> ""
                })
            prefs.edit().putString(cacheKey, payload.toString()).apply()
            publish("rushHomeFeedResults", payload.toString())
        }
    }

    @JavascriptInterface
    fun refreshHomeFeed(mode: String) {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        val sources = FeedPreferences.sources(prefs)
        val profile = discoveryProfile(prefs)
        val signature = profile.topics.joinToString("|") + "|" +
            profile.showMoreTopics.sorted().joinToString("|") + "|" +
            sources.joinToString("|") { "${it.kind}:${it.id}:${it.name}" } + "|" +
            prefs.getString("rush_video_feedback", "{}")
        val key = "rush_home_feed_v5_${if (mode.equals("following", true)) "following" else "forYou"}_${Integer.toHexString(signature.hashCode())}"
        prefs.edit().remove(key).apply()
        loadHomeFeed(mode)
    }

    @JavascriptInterface
    fun loadMusicSuggestions() {
        val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
        activity.lifecycleScope.launch {
            val follows = FeedPreferences.sources(prefs).filter { it.kind == "artist" }
            val profile = discoveryProfile(prefs)
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
                            val legacyTaste = queryWeight + artistScore + tasteTermsScore + followedArtistBoost
                            val candidate = RushDiscoveryEngine.Candidate(
                                id = item.videoId, kind = RushDiscoveryEngine.Kind.MUSIC,
                                title = item.title, creator = item.creator,
                                sourceFollowed = followedArtistBoost > 0,
                                durationSeconds = parseDurationSeconds(item.duration)
                            )
                            val sharedMusicScore = RushDiscoveryEngine.scoreFeed(candidate, profile)
                            val score = (sharedMusicScore * 0.72 +
                                (legacyTaste.coerceAtMost(800) / 800.0) * 1000.0 * 0.28).toInt()
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
            val profile = discoveryProfile(prefs)
            val followedPublishers = FeedPreferences.sources(prefs).filter { it.kind == "news" }
            val queries = (profile.topics.take(3) + followedPublishers.take(2).map { it.name }).distinct()
            val hits = LinkedHashMap<String, RushDiscoveryEngine.FeedHit>()
            val payloads = LinkedHashMap<String, JSONObject>()
            val results = withContext(Dispatchers.IO) {
                coroutineScope {
                    val slots = Semaphore(4)
                    queries.map { query ->
                        async {
                            query to runCatching { slots.withPermit { requestNews(query) } }
                                .getOrDefault(JSONArray())
                        }
                    }.awaitAll()
                }
            }
            results.forEach { (_, items) ->
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val publisher = item.optString("source")
                    val followed = followedPublishers.any {
                        publisher.contains(it.name, true) || it.name.contains(publisher, true)
                    }
                    val candidate = newsCandidate(item, followed)
                    val score = RushDiscoveryEngine.scoreFeed(candidate, profile)
                    val key = item.optString("url")
                    val previous = hits[key]
                    if (key.isNotBlank() && (previous == null || score > previous.score)) {
                        hits[key] = RushDiscoveryEngine.FeedHit(candidate, score)
                        payloads[key] = item
                    }
                }
            }
            val articles = RushDiscoveryEngine.diversifyFeed(hits.values.toList(), 32, 3)
                .mapNotNull { payloads[it.candidate.id] }
            publish("rushNewsResults", JSONArray(articles).toString())
            val enriched = enrichNewsThumbnails(articles, prefs, 16)
            if (enriched.any { it.optString("thumbnail").isNotBlank() }) {
                publish("rushNewsResults", JSONArray(enriched).toString())
            }
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
                    val raw = LiveYouTubeSearch.search(term, music = true).filter { it.videoId.isNotBlank() }
                    rankYouTubeSearch(term, raw, RushDiscoveryEngine.Kind.MUSIC).take(30)
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
                activity.hideNativeVideo()
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
    fun queueVideo(videoId: String, title: String, creator: String, thumbnail: String) {
        if (!videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) return
        activity.lifecycleScope.launch {
            try {
                val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
                val quality = prefs.getInt("video_quality", 0)
                val preferMp4 = prefs.getBoolean("download_mp4", false)
                val resolved = withContext(Dispatchers.IO) {
                    resolvePlaybackMedia(videoId, audioOnly = false, maxHeight = quality.takeIf { it > 0 },
                        preferMp4 = preferMp4, preferDash = !preferMp4, allowDashFallback = !preferMp4)
                }
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
                val controller = getController()
                if ((0 until controller.mediaItemCount).none { controller.getMediaItemAt(it).mediaId == videoId }) {
                    controller.addMediaItem(item)
                }
            } catch (error: Exception) {
                publish("rushVideoQueueError", JSONObject().put("message", playbackErrorMessage(error, "Could not queue the next video.")).toString())
            }
        }
    }

    @JavascriptInterface
    fun queueVideos(itemsJson: String) {
        val items = runCatching { JSONArray(itemsJson) }.getOrDefault(JSONArray())
        if (items.length() == 0) return
        activity.lifecycleScope.launch {
            try {
                val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
                val controller = getController()
                val quality = prefs.getInt("video_quality", 0)
                val preferMp4 = prefs.getBoolean("download_mp4", false)
                for (index in 0 until minOf(items.length(), 12)) {
                    val data = items.optJSONObject(index) ?: continue
                    val id = data.optString("id")
                    if (!id.matches(Regex("[A-Za-z0-9_-]{11}"))) continue
                    if ((0 until controller.mediaItemCount).any { controller.getMediaItemAt(it).mediaId == id }) continue
                    val resolved = withContext(Dispatchers.IO) {
                        resolvePlaybackMedia(id, audioOnly = false, maxHeight = quality.takeIf { it > 0 },
                            preferMp4 = preferMp4, preferDash = !preferMp4, allowDashFallback = !preferMp4)
                    }
                    val title = data.optString("title")
                    val creator = data.optString("creator")
                    val thumbnail = data.optString("thumbnail")
                    val subtitles = resolved.subtitles.map { subtitle ->
                        MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitle.url))
                            .setMimeType(subtitle.mimeType).setLanguage(subtitle.languageTag)
                            .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                            .setLabel(subtitle.label).build()
                    }
                    val metadata = MediaMetadata.Builder().setTitle(title).setArtist(creator)
                        .apply { if (thumbnail.isNotBlank()) setArtworkUri(android.net.Uri.parse(thumbnail)) }
                        .setExtras(PlaybackMetadata.extras(id, false, quality, preferMp4)).build()
                    controller.addMediaItem(MediaItem.Builder().setMediaId(id).setUri(resolved.url)
                        .setMimeType(resolved.mimeType).setMediaMetadata(metadata)
                        .setSubtitleConfigurations(subtitles).build())
                }
            } catch (error: Exception) {
                publish("rushVideoQueueError", JSONObject().put("message", playbackErrorMessage(error, "Could not prepare the next videos.")).toString())
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
                    val raw = LiveYouTubeSearch.search(term, music = false).filter { it.videoId.isNotBlank() }
                    rankYouTubeSearch(term, raw, RushDiscoveryEngine.Kind.VIDEO).take(30)
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
                activity.runOnUiThread { activity.showNativeVideo(controller, fullscreen = false) }
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
                if (!audioOnly) activity.runOnUiThread { activity.showNativeVideo(controller, fullscreen = false) }
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

    private fun completedController(): MediaController? {
        val future = controllerFuture ?: return null
        if (!future.isDone || future.isCancelled) return null
        return runCatching { future.get() }.getOrNull()
    }

    @JavascriptInterface
    fun musicPosition(): Long = completedController()?.currentPosition ?: 0L

    @JavascriptInterface
    fun musicDuration(): Long = completedController()?.duration?.takeIf { it > 0L } ?: 0L

    @JavascriptInterface
    fun seekPlaybackTo(positionMs: Long) {
        activity.lifecycleScope.launch {
            runCatching {
                val controller = getController()
                val duration = controller.duration.takeIf { it > 0L } ?: positionMs.coerceAtLeast(0L)
                controller.seekTo(positionMs.coerceIn(0L, duration))
            }.onFailure {
                publish("rushMusicPlaybackState", JSONObject().put("state", "error")
                    .put("message", it.message ?: "Could not seek this track.").toString())
            }
        }
    }

    @JavascriptInterface
    fun skipPrevious() {
        activity.lifecycleScope.launch {
            runCatching {
                val controller = getController()
                if (controller.hasPreviousMediaItem()) controller.seekToPreviousMediaItem()
                else controller.seekTo(0L)
                loadCurrentPlayback()
            }
        }
    }

    @JavascriptInterface
    fun skipNext() {
        activity.lifecycleScope.launch {
            runCatching {
                val controller = getController()
                if (controller.hasNextMediaItem()) {
                    controller.seekToNextMediaItem()
                    loadCurrentPlayback()
                } else {
                    publish("rushMusicPlaybackState", JSONObject()
                        .put("state", "info")
                        .put("message", "No next track is loaded. Add tracks with QUEUE, then play them from Library → Queue.")
                        .toString())
                }
            }.onFailure {
                publish("rushMusicPlaybackState", JSONObject()
                    .put("state", "error").put("message", it.message ?: "Could not skip this track.").toString())
            }
        }
    }

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
        fetchNewsRecommendations(query)
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
            if (!cached.isNullOrBlank()) runCatching { rankNewsItems(query, JSONArray(cached)) }.getOrNull()?.let {
                publish("rushNewsResults", it.toString())
            }
            else publish("rushNewsLoading", JSONObject().put("query", query).toString())
            try {
                val ranked = withContext(Dispatchers.IO) { rankNewsItems(query, requestNews(query)) }
                val articles = (0 until ranked.length()).mapNotNull { ranked.optJSONObject(it) }
                val enriched = enrichNewsThumbnails(articles, prefs, 16)
                val output = JSONArray(enriched)
                prefs.edit().putString(cacheKey, output.toString()).apply()
                publish("rushNewsResults", output.toString())
            } catch (error: Exception) {
                if (cached.isNullOrBlank()) publish("rushNewsError", JSONObject().put("message", playbackErrorMessage(error, "Could not load news.")).toString())
            }
        }
    }
    private suspend fun enrichNewsThumbnails(
        items: List<JSONObject>,
        prefs: android.content.SharedPreferences,
        limit: Int = 12
    ): List<JSONObject> {
        if (items.isEmpty() || limit <= 0) return items
        val cachedImages = runCatching {
            JSONObject(prefs.getString(NEWS_THUMBNAIL_CACHE_KEY, "{}") ?: "{}")
        }.getOrDefault(JSONObject())
        val imageSlots = Semaphore(4)
        val results = coroutineScope {
            items.take(limit).map { article ->
                async(Dispatchers.IO) {
                    if (article.optString("thumbnail").startsWith("https://")) {
                        return@async article to ""
                    }
                    val articleUrl = article.optString("url").trim()
                    if (articleUrl.isBlank() || !articleUrl.startsWith("https://")) {
                        return@async article to ""
                    }
                    val cachedEntry = cachedImages.optJSONObject(articleUrl)
                    if (cachedEntry != null) {
                        val cachedImage = cachedEntry.optString("image")
                        val cachedAt = cachedEntry.optLong("at", 0L)
                        val cacheAge = System.currentTimeMillis() - cachedAt
                        val cacheIsValid = cachedAt > 0L && if (cachedImage.isNotBlank()) {
                            cacheAge in 0..(7L * 24 * 60 * 60 * 1000)
                        } else {
                            cacheAge in 0..(6L * 60 * 60 * 1000)
                        }
                        if (cacheIsValid) return@async article to cachedImage
                    }
                    val image = imageSlots.withPermit { extractArticleThumbnail(articleUrl) }
                    article to image
                }
            }.awaitAll()
        }

        results.forEach { (article, image) ->
            val articleUrl = article.optString("url").trim()
            if (articleUrl.isBlank() || article.optString("thumbnail").startsWith("https://")) return@forEach
            cachedImages.put(articleUrl, JSONObject()
                .put("image", image)
                .put("at", System.currentTimeMillis()))
            if (image.startsWith("https://")) article.put("thumbnail", image)
        }
        while (cachedImages.length() > 160) {
            val keys = cachedImages.keys()
            var oldestKey: String? = null
            var oldestAt = Long.MAX_VALUE
            while (keys.hasNext()) {
                val key = keys.next()
                val at = cachedImages.optJSONObject(key)?.optLong("at", 0L) ?: 0L
                if (at < oldestAt) {
                    oldestAt = at
                    oldestKey = key
                }
            }
            if (oldestKey == null) break
            cachedImages.remove(oldestKey)
        }
        prefs.edit().putString(NEWS_THUMBNAIL_CACHE_KEY, cachedImages.toString()).apply()
        return items
    }

    private fun normalizeNewsImageUrl(value: String): String {
        var candidate = android.text.Html.fromHtml(
            value.trim(),
            android.text.Html.FROM_HTML_MODE_LEGACY
        ).toString().replace("&amp;", "&").trim()
        if (candidate.startsWith("//")) candidate = "https:$candidate"
        val uri = runCatching { android.net.Uri.parse(candidate) }.getOrNull() ?: return ""
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || !uri.userInfo.isNullOrBlank()) return ""
        return candidate
    }

    private fun imageFromRssMarkup(markup: String): String {
        val tag = Regex("""(?is)<img\b[^>]*>""").find(markup)?.value ?: return ""
        val attrs = Regex("""([a-zA-Z:-]+)\s*=\s*["']([^"']*)["']""")
            .findAll(tag).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        val raw = attrs["src"] ?: attrs["data-src"] ?: attrs["data-original"] ?:
            attrs["srcset"]?.substringBefore(",")?.trim()?.substringBefore(" ")
        return normalizeNewsImageUrl(raw.orEmpty())
    }

    private fun requestNews(query: String): JSONArray {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val request = okhttp3.Request.Builder()
            .url("https://news.google.com/rss/search?q=$encoded&hl=en&gl=US&ceid=US:en")
            .header("User-Agent", "Rush/1.0 (Android)")
            .build()
        NEWS_RSS_CLIENT.newCall(request).execute().use { response ->
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
                            val localName = parser.name.substringAfter(":").lowercase()
                            val type = parser.getAttributeValue(null, "type").orEmpty().lowercase()
                            val medium = parser.getAttributeValue(null, "medium").orEmpty().lowercase()
                            val isImageElement = localName == "thumbnail" ||
                                (localName == "content" && (type.startsWith("image/") || medium == "image")) ||
                                (localName == "enclosure" && type.startsWith("image/"))
                            if (isImageElement) {
                                val candidate = normalizeNewsImageUrl(parser.getAttributeValue(null, "url").orEmpty())
                                if (candidate.isNotBlank()) thumbnail = candidate
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
                            if (thumbnail.isBlank()) thumbnail = imageFromRssMarkup(description)
                            val plainTitle = android.text.Html.fromHtml(
                                title, android.text.Html.FROM_HTML_MODE_LEGACY
                            ).toString().replace('\u00A0', ' ').replace(Regex("\\s+"), " ").trim()
                            val cleanSummary = android.text.Html.fromHtml(
                                description, android.text.Html.FROM_HTML_MODE_LEGACY
                            ).toString().replace('\u00A0', ' ').replace(Regex("\\s+"), " ").trim()
                            if (plainTitle.isNotBlank() && link.trim().startsWith("https://")) {
                                output.put(JSONObject()
                                    .put("title", plainTitle)
                                    .put("url", link.trim())
                                    .put("date", date.trim())
                                    .put("source", source.trim().ifBlank { "News source" })
                                    .put("summary", cleanSummary.take(280))
                                    .put("thumbnail", thumbnail))
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

    private fun extractArticleThumbnail(articleUrl: String): String {
        return runCatching {
            val uri = android.net.Uri.parse(articleUrl)
            if (uri.scheme != "https" || uri.host.isNullOrBlank() || !uri.userInfo.isNullOrBlank()) return ""
            val request = okhttp3.Request.Builder().url(articleUrl)
                .header("User-Agent", "Mozilla/5.0 (compatible; Rush/1.0)")
                .header("Accept", "text/html,application/xhtml+xml").build()
            NEWS_ARTICLE_CLIENT.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use ""
                val body = response.body ?: return@use ""
                val source = body.charStream().use { reader ->
                    val outputText = StringBuilder()
                    val buffer = CharArray(4096)
                    while (outputText.length < 64 * 1024) {
                        val count = reader.read(buffer, 0, minOf(buffer.size, 64 * 1024 - outputText.length))
                        if (count <= 0) break
                        outputText.append(buffer, 0, count)
                    }
                    outputText.toString()
                }
                var imageUrl = ""
                for (tag in Regex("""(?is)<meta\b[^>]*>""").findAll(source)) {
                    val attrs = Regex("""([a-zA-Z:-]+)\s*=\s*["']([^"']*)["']""")
                        .findAll(tag.value).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    val names = listOf(attrs["property"], attrs["name"]).mapNotNull { it?.lowercase() }
                    if (names.any { it in setOf("og:image", "og:image:url", "og:image:secure_url", "twitter:image", "twitter:image:src") }) {
                        imageUrl = attrs["content"].orEmpty()
                        if (imageUrl.isNotBlank()) break
                    }
                }
                if (imageUrl.isBlank()) {
                    for (tag in Regex("""(?is)<link\b[^>]*>""").findAll(source)) {
                        val attrs = Regex("""([a-zA-Z:-]+)\s*=\s*["']([^"']*)["']""")
                            .findAll(tag.value).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                        if (attrs["rel"].orEmpty().contains("image_src", true)) {
                            imageUrl = attrs["href"].orEmpty()
                            if (imageUrl.isNotBlank()) break
                        }
                    }
                }
                if (imageUrl.isBlank()) {
                    imageUrl = Regex("""(?is)["'](?:image|thumbnailUrl)["']\s*:\s*["']([^"']+)["']""")
                        .find(source)?.groupValues?.getOrNull(1).orEmpty()
                }
                val decoded = android.text.Html.fromHtml(
                    imageUrl.replace("&amp;", "&"),
                    android.text.Html.FROM_HTML_MODE_LEGACY
                ).toString().trim()
                val resolved = decoded.takeIf { it.isNotBlank() }?.let { response.request.url.resolve(it) }
                resolved?.takeIf { it.isHttps && it.host.isNotBlank() }?.toString().orEmpty()
            }
        }.getOrDefault("")
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
                    activity.runOnUiThread { activity.showNativeVideo(controller, fullscreen = true) }
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
                else activity.runOnUiThread { activity.showNativeVideo(controller, fullscreen = false) }
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

    private fun rankYouTubeSearch(query: String, results: List<YouTubeSearchResult>, kind: RushDiscoveryEngine.Kind): List<YouTubeSearchResult> {
        val documents = results.map { item ->
            RushDiscoveryEngine.SearchDocument(item, item.title, item.creator, item.description, kind)
        }
        return RushDiscoveryEngine.rankSearch(query, documents).map { it.value }
    }

    private fun rankNewsItems(query: String, items: JSONArray): JSONArray {
        val rows = (0 until items.length()).mapNotNull { items.optJSONObject(it) }
        val documents = rows.map { item ->
            RushDiscoveryEngine.SearchDocument(item, item.optString("title"), item.optString("source"),
                item.optString("summary"), RushDiscoveryEngine.Kind.NEWS)
        }
        return JSONArray(RushDiscoveryEngine.rankSearch(query, documents).map { it.value })
    }

    private fun discoveryProfile(prefs: android.content.SharedPreferences): RushDiscoveryEngine.Profile {
        val sources = FeedPreferences.sources(prefs)
        val followed = sources.map { it.name }.filter(String::isNotBlank).toSet()
        val historyTerms = LinkedHashSet<String>()
        val recentIds = LinkedHashSet<String>()
        val likedTerms = LinkedHashSet<String>()
        val dislikedTerms = LinkedHashSet<String>()
        runCatching {
            val library = LocalLibrary.load(prefs)
            val history = library.optJSONArray("history") ?: JSONArray()
            val items = library.optJSONObject("items") ?: JSONObject()
            for (index in 0 until minOf(history.length(), 35)) {
                val id = history.optString(index)
                if (id.isBlank()) continue
                recentIds += id
                val metadata = items.optJSONObject(id) ?: continue
                historyTerms += historyTokens(metadata.optString("title") + " " + metadata.optString("creator"))
            }
        }
        runCatching {
            val history = JSONArray(prefs.getString("music_history", "[]") ?: "[]")
            for (index in 0 until minOf(history.length(), 25)) {
                val item = history.optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (id.isNotBlank()) recentIds += id
                historyTerms += historyTokens(item.optString("title") + " " + item.optString("artist"))
            }
        }
        runCatching {
            val feedback = JSONObject(prefs.getString("rush_video_feedback", "{}") ?: "{}")
            feedback.keys().forEach { id ->
                val item = feedback.optJSONObject(id) ?: return@forEach
                val terms = historyTokens(item.optString("title") + " " + item.optString("creator"))
                when (item.optInt("rating")) {
                    1 -> likedTerms += terms
                    -1 -> dislikedTerms += terms
                }
            }
        }
        val modes = FeedPreferences.topicModes(prefs)
        return RushDiscoveryEngine.Profile(
            topics = FeedPreferences.topics(prefs),
            followedCreators = followed,
            historyTerms = historyTerms.take(80).toSet(),
            recentIds = recentIds,
            likedTerms = likedTerms.take(100).toSet(),
            dislikedTerms = dislikedTerms.take(100).toSet(),
            showMoreTopics = modes.filterValues { it == "more" }.keys
        )
    }

    private fun historyTokens(value: String): Set<String> = value.lowercase(java.util.Locale.ROOT)
        .split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }.toSet()

    private fun newsCandidate(item: JSONObject, followed: Boolean): RushDiscoveryEngine.Candidate =
        RushDiscoveryEngine.Candidate(
            id = item.optString("url"), kind = RushDiscoveryEngine.Kind.NEWS,
            title = item.optString("title"), creator = item.optString("source"),
            summary = item.optString("summary"),
            publishedAtEpochSeconds = RushDiscoveryEngine.parseRssDate(item.optString("date")),
            sourceFollowed = followed
        )

    private fun fetchNewsRecommendations(query: String) {
        activity.lifecycleScope.launch {
            val prefs = activity.getSharedPreferences("rush_prefs", android.content.Context.MODE_PRIVATE)
            publish("rushNewsLoading", JSONObject().put("query", query).toString())
            try {
                val profile = discoveryProfile(prefs)
                val followed = FeedPreferences.sources(prefs).filter { it.kind == "news" }
                val items = withContext(Dispatchers.IO) { requestNews(query) }
                val rows = (0 until items.length()).mapNotNull { items.optJSONObject(it) }
                val hits = rows.map { item ->
                    val publisher = item.optString("source")
                    val isFollowed = followed.any { publisher.contains(it.name, true) || it.name.contains(publisher, true) }
                    val candidate = newsCandidate(item, isFollowed)
                    RushDiscoveryEngine.FeedHit(candidate, RushDiscoveryEngine.scoreFeed(candidate, profile))
                }
                val byId = rows.associateBy { it.optString("url") }
                val ranked = RushDiscoveryEngine.diversifyFeed(hits, 40, 3).mapNotNull { byId[it.candidate.id] }
                publish("rushNewsResults", JSONArray(ranked).toString())
                val enriched = enrichNewsThumbnails(ranked, prefs, 16)
                if (enriched.any { it.optString("thumbnail").isNotBlank() }) {
                    publish("rushNewsResults", JSONArray(enriched).toString())
                }
            } catch (error: Exception) {
                publish("rushNewsError", JSONObject().put("message", playbackErrorMessage(error, "Could not load news.")).toString())
            }
        }
    }

    private fun parseDurationSeconds(value: String): Long = runCatching {
        val parts = value.trim().split(":").map(String::toLong)
        when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0L
        }
    }.getOrDefault(0L)

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
