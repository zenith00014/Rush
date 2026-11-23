package com.rush.app

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class FeedSource(
    val kind: String,
    val id: String,
    val name: String,
    val productive: Boolean
)

object FeedPreferences {
    private const val TOPICS_KEY = "rush_feed_topics"
    private const val SOURCES_KEY = "rush_followed_sources"
    private const val SNOOZED_TOPICS_KEY = "rush_snoozed_topics"
    private const val TOPIC_SNOOZE_MS = 24L * 60L * 60L * 1000L

    val defaultTopics = listOf(
        "Science", "Technology", "History", "Education", "Design", "Engineering",
        "Economics", "Culture", "Environment", "Sports", "Music", "Health",
        "Language", "Business", "Art", "Documentaries"
    )

    val topicCatalog = listOf(
        "Science", "Technology", "AI and machine learning", "Programming", "Cybersecurity",
        "History", "Geography", "Education", "Languages", "Literature", "Books",
        "Philosophy", "Psychology", "Mathematics", "Space", "Engineering", "Economics",
        "Business", "Startups", "Personal finance", "Investing", "Politics", "Current affairs",
        "World news", "Environment", "Climate", "Health", "Medicine", "Fitness", "Sports",
        "Football", "Basketball", "Cricket", "Esports", "Gaming", "Game development",
        "Design", "Art", "Photography", "Architecture", "Fashion", "Beauty", "Makeup",
        "Cooking", "Food", "Travel", "Cars", "Motorcycles", "Nature", "Wildlife", "Pets",
        "Gardening", "Home improvement", "Music", "Music production", "Classical music",
        "Hip-hop", "K-pop", "Film analysis", "Anime", "Manga", "Podcasts", "Documentaries",
        "Vlogs", "Comedy", "Memes", "Viral clips", "Celebrity gossip", "Pranks",
        "Internet drama", "Rage bait", "Reality TV", "Shorts", "ASMR", "Luxury",
        "Shopping", "Product reviews", "Relationships", "Parenting", "Lifestyle",
        "Meditation", "Self-improvement", "Romance", "Horror", "Internet culture",
        "Streaming", "Live streams", "Board games", "Collecting", "Crafts"
    )

    private val lowAttentionTerms = listOf(
        "brainrot", "meme", "memes", "prank", "pranks", "rage bait", "ragebait",
        "celebrity gossip", "influencer drama", "internet drama", "viral challenge",
        "viral clip", "reaction prank", "clickbait"
    )

    private val contextTerms = listOf(
        "explained", "analysis", "documentary", "documentaries", "research",
        "lecture", "tutorial", "how it works", "deep dive", "history of",
        "investigation", "report", "review", "guide", "science", "engineering"
    )

    /** Every followed YouTube channel may supply For You candidates; productivity only affects ranking. */
    fun forYouChannels(sources: List<FeedSource>): List<FeedSource> =
        sources.filter { it.kind == "channel" }

    fun allTopics(prefs: SharedPreferences): List<String> {
        val stored = runCatching { JSONArray(prefs.getString(TOPICS_KEY, null) ?: "[]") }
            .getOrDefault(JSONArray())
        val values = (0 until stored.length()).mapNotNull { stored.optString(it).trim().takeIf { topic -> topic in topicCatalog } }
        return values.distinct().ifEmpty { defaultTopics }
    }

    fun snoozedTopics(prefs: SharedPreferences): Map<String, Long> {
        val stored = runCatching { JSONObject(prefs.getString(SNOOZED_TOPICS_KEY, "{}") ?: "{}") }
            .getOrDefault(JSONObject())
        val now = System.currentTimeMillis()
        val active = LinkedHashMap<String, Long>()
        val expired = mutableListOf<String>()
        stored.keys().forEach { topic ->
            val until = stored.optLong(topic)
            if (topic in topicCatalog && until > now) active[topic] = until else expired += topic
        }
        if (expired.isNotEmpty()) {
            expired.forEach(stored::remove)
            prefs.edit().putString(SNOOZED_TOPICS_KEY, stored.toString()).apply()
        }
        return active
    }

    fun topics(prefs: SharedPreferences): List<String> {
        val snoozed = snoozedTopics(prefs).keys
        return allTopics(prefs).filterNot { it in snoozed }
    }

    fun snoozeTopic(prefs: SharedPreferences, rawTopic: String): Long {
        val topic = rawTopic.trim()
        require(topic in allTopics(prefs)) { "Choose a followed topic to snooze." }
        val values = JSONObject(prefs.getString(SNOOZED_TOPICS_KEY, "{}") ?: "{}")
            .put(topic, System.currentTimeMillis() + TOPIC_SNOOZE_MS)
        prefs.edit().putString(SNOOZED_TOPICS_KEY, values.toString()).apply()
        return values.getLong(topic)
    }

    fun unsnoozeTopic(prefs: SharedPreferences, rawTopic: String) {
        val values = JSONObject(prefs.getString(SNOOZED_TOPICS_KEY, "{}") ?: "{}")
        values.remove(rawTopic.trim())
        prefs.edit().putString(SNOOZED_TOPICS_KEY, values.toString()).apply()
    }

    fun sources(prefs: SharedPreferences): List<FeedSource> {
        val stored = runCatching { JSONArray(prefs.getString(SOURCES_KEY, "[]") ?: "[]") }
            .getOrDefault(JSONArray())
        return buildList {
            for (index in 0 until stored.length()) {
                val item = stored.optJSONObject(index) ?: continue
                val kind = item.optString("kind").lowercase(Locale.ROOT)
                val id = item.optString("id").trim()
                val name = item.optString("name").trim()
                if (kind !in setOf("channel", "news", "artist") || id.isBlank() || name.isBlank()) continue
                add(FeedSource(kind, id, name, item.optBoolean("productive", false)))
            }
        }
    }

    fun follow(prefs: SharedPreferences, kind: String, id: String, name: String, productive: Boolean = false) {
        val normalizedKind = kind.trim().lowercase(Locale.ROOT)
        val cleanId = id.trim().take(256)
        val cleanName = name.trim().take(160)
        require(normalizedKind in setOf("channel", "news", "artist")) { "Unsupported source type." }
        require(cleanId.isNotBlank() && cleanName.isNotBlank()) { "A source name and ID are required." }
        val updated = sources(prefs).toMutableList()
        val index = updated.indexOfFirst { it.kind == normalizedKind && it.id == cleanId }
        val source = FeedSource(normalizedKind, cleanId, cleanName, productive)
        if (index >= 0) updated[index] = source else updated += source
        writeSources(prefs, updated)
    }

    fun unfollow(prefs: SharedPreferences, kind: String, id: String) {
        val updated = sources(prefs).filterNot {
            it.kind == kind.trim().lowercase(Locale.ROOT) && it.id == id.trim()
        }
        writeSources(prefs, updated)
    }

    fun setProductive(prefs: SharedPreferences, id: String, name: String, enabled: Boolean) {
        val updated = sources(prefs).toMutableList()
        val index = updated.indexOfFirst { it.kind == "channel" && it.id == id.trim() }
        val source = FeedSource("channel", id.trim(), name.trim(), enabled)
        if (index >= 0) updated[index] = updated[index].copy(productive = enabled) else updated += source
        writeSources(prefs, updated)
    }

    fun saveTopics(prefs: SharedPreferences, raw: String): List<String> {
        val array = JSONArray(raw)
        val selected = (0 until array.length())
            .mapNotNull { array.optString(it).trim().takeIf { value -> value in topicCatalog } }
            .distinct()
        require(selected.isNotEmpty()) { "Choose at least one topic." }
        val snoozed = JSONObject(prefs.getString(SNOOZED_TOPICS_KEY, "{}") ?: "{}")
        selected.forEach(snoozed::remove)
        prefs.edit()
            .putString(TOPICS_KEY, JSONArray(selected).toString())
            .putString(SNOOZED_TOPICS_KEY, snoozed.toString())
            .apply()
        return selected
    }

    fun asJson(prefs: SharedPreferences): JSONObject {
        val topicArray = JSONArray(topics(prefs))
        val sourceArray = JSONArray().apply {
            sources(prefs).forEach {
                put(JSONObject().put("kind", it.kind).put("id", it.id).put("name", it.name)
                    .put("productive", it.productive))
            }
        }
        val allTopics = JSONArray(allTopics(prefs))
        val snoozedArray = JSONArray().apply {
            snoozedTopics(prefs).forEach { (topic, until) ->
                put(JSONObject().put("topic", topic).put("until", until))
            }
        }
        return JSONObject()
            .put("topics", topicArray)
            .put("allTopics", allTopics)
            .put("snoozedTopics", snoozedArray)
            .put("sources", sourceArray)
    }

    /** Compatibility wrapper; new recommendation paths use the shared typed scoring engine. */
    fun rank(title: String, description: String, topics: List<String>): Int =
        RushDiscoveryEngine.legacyProductivityScore(title, description, topics)

    private fun writeSources(prefs: SharedPreferences, sources: List<FeedSource>) {
        val array = JSONArray()
        sources.forEach {
            array.put(JSONObject().put("kind", it.kind).put("id", it.id).put("name", it.name)
                .put("productive", it.productive))
        }
        prefs.edit().putString(SOURCES_KEY, array.toString()).apply()
    }
}
