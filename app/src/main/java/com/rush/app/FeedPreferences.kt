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
    private const val TOPIC_MODES_KEY = "rush_topic_modes"
    private const val SNOOZED_TOPICS_KEY = "rush_snoozed_topics"
    private const val DAY_MS = 24L * 60L * 60L * 1000L

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
        "Internet drama", "Rage bait", "Reality TV", "ASMR", "Luxury",
        "Shopping", "Product reviews", "Relationships", "Parenting", "Lifestyle",
        "Meditation", "Self-improvement", "Romance", "Horror", "Internet culture",
        "Streaming", "Live streams", "Board games", "Collecting", "Crafts"
    )

    private val topicAliases = listOf(
        listOf("artificial intelligence", "machine learning", "llm", "large language model", "generative ai") to "AI and machine learning",
        listOf("coding", "software development", "web development", "python", "javascript", "java", "kotlin") to "Programming",
        listOf("game dev", "unity", "unreal engine", "game design") to "Game development",
        listOf("money management", "budgeting", "saving money") to "Personal finance",
        listOf("mental health", "therapy", "brain science") to "Psychology",
        listOf("space exploration", "astronomy", "astrophysics") to "Space",
        listOf("climate change", "global warming", "sustainability") to "Environment",
        listOf("workouts", "exercise", "strength training") to "Fitness",
        listOf("cooking recipes", "baking", "meal prep") to "Cooking",
        listOf("graphic design", "ui design", "ux design", "product design") to "Design",
        listOf("filmmaking", "cinematography", "movie breakdown") to "Film analysis",
        listOf("sound design", "beat making", "music recording") to "Music production"
    )

    fun allTopics(prefs: SharedPreferences): List<String> {
        val stored = runCatching { JSONArray(prefs.getString(TOPICS_KEY, null) ?: "[]") }
            .getOrDefault(JSONArray())
        val values = (0 until stored.length())
            .mapNotNull { stored.optString(it).trim().takeIf(String::isNotBlank) }
        return values.filterNot { it.equals("Shorts", ignoreCase = true) }.distinct().ifEmpty { defaultTopics }
    }

    fun topics(prefs: SharedPreferences): List<String> {
        val now = System.currentTimeMillis()
        val modes = topicModes(prefs)
        val snoozed = snoozedTopics(prefs).filterValues { it > now }.keys
        return allTopics(prefs).filter { modes[it] != "hide" && it !in snoozed }
    }

    fun topicModes(prefs: SharedPreferences): Map<String, String> {
        val json = runCatching { JSONObject(prefs.getString(TOPIC_MODES_KEY, "{}") ?: "{}") }
            .getOrDefault(JSONObject())
        return json.keys().asSequence().mapNotNull { key ->
            json.optString(key).takeIf { it == "more" || it == "hide" }?.let { key to it }
        }.toMap()
    }

    fun setTopicMode(prefs: SharedPreferences, topic: String, mode: String) {
        val clean = cleanTopic(topic)
        require(clean in allTopics(prefs)) { "Topic not found." }
        require(mode in setOf("more", "hide", "normal")) { "Invalid topic preference." }
        val json = runCatching { JSONObject(prefs.getString(TOPIC_MODES_KEY, "{}") ?: "{}") }
            .getOrDefault(JSONObject())
        if (mode == "normal") json.remove(clean) else json.put(clean, mode)
        prefs.edit().putString(TOPIC_MODES_KEY, json.toString()).apply()
    }

    fun snoozedTopics(prefs: SharedPreferences): Map<String, Long> {
        val json = runCatching { JSONObject(prefs.getString(SNOOZED_TOPICS_KEY, "{}") ?: "{}") }
            .getOrDefault(JSONObject())
        val now = System.currentTimeMillis()
        val values = json.keys().asSequence().mapNotNull { key ->
            json.optLong(key, 0L).takeIf { it > now }?.let { key to it }
        }.toMap()
        if (values.size != json.length()) {
            prefs.edit().putString(SNOOZED_TOPICS_KEY, JSONObject(values.mapValues { it.value }).toString()).apply()
        }
        return values
    }

    fun snoozeTopic(prefs: SharedPreferences, topic: String): Long {
        val clean = cleanTopic(topic)
        require(clean in allTopics(prefs)) { "Topic not found." }
        val until = System.currentTimeMillis() + DAY_MS
        val json = runCatching { JSONObject(prefs.getString(SNOOZED_TOPICS_KEY, "{}") ?: "{}") }
            .getOrDefault(JSONObject())
        json.put(clean, until)
        prefs.edit().putString(SNOOZED_TOPICS_KEY, json.toString()).apply()
        return until
    }

    fun unsnoozeTopic(prefs: SharedPreferences, topic: String) {
        val json = runCatching { JSONObject(prefs.getString(SNOOZED_TOPICS_KEY, "{}") ?: "{}") }
            .getOrDefault(JSONObject())
        json.remove(cleanTopic(topic))
        prefs.edit().putString(SNOOZED_TOPICS_KEY, json.toString()).apply()
    }

    /** Local topic resolver maps natural-language requests to the closest known subject. */
    fun resolveTopic(raw: String): String {
        val clean = cleanTopic(raw)
        require(clean.length >= 2) { "Enter a topic with at least two characters." }
        require(clean.length <= 64) { "Keep topics under 64 characters." }
        val normalized = clean.lowercase(Locale.ROOT)
        topicAliases.firstOrNull { (aliases, _) -> aliases.any { normalized.contains(it) } }?.let { return it.second }
        topicCatalog.firstOrNull { it.equals(clean, true) }?.let { return it }
        return clean.replace(Regex("\\s+"), " ").split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }

    fun addTopic(prefs: SharedPreferences, raw: String): String {
        val topic = resolveTopic(raw)
        val updated = (allTopics(prefs) + topic).distinct().take(100)
        prefs.edit().putString(TOPICS_KEY, JSONArray(updated).toString()).apply()
        return topic
    }

    fun removeTopic(prefs: SharedPreferences, topic: String): List<String> {
        val clean = cleanTopic(topic)
        val updated = allTopics(prefs).filterNot { it.equals(clean, true) }
        require(updated.isNotEmpty()) { "Keep at least one topic." }
        prefs.edit().putString(TOPICS_KEY, JSONArray(updated).toString()).apply()
        setTopicModeIfPresent(prefs, clean, null)
        unsnoozeTopic(prefs, clean)
        return updated
    }

    private fun setTopicModeIfPresent(prefs: SharedPreferences, topic: String, mode: String?) {
        val json = runCatching { JSONObject(prefs.getString(TOPIC_MODES_KEY, "{}") ?: "{}") }
            .getOrDefault(JSONObject())
        if (mode == null) json.remove(topic) else json.put(topic, mode)
        prefs.edit().putString(TOPIC_MODES_KEY, json.toString()).apply()
    }

    private fun cleanTopic(value: String): String = value.trim()
        .replace(Regex("[\\p{Cntrl}<>]"), "")
        .replace(Regex("\\s+"), " ")
        .take(64)

    /** Every followed YouTube channel may supply For You candidates; productivity only affects ranking. */
    fun forYouChannels(sources: List<FeedSource>): List<FeedSource> =
        sources.filter { it.kind == "channel" }

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
            .mapNotNull { array.optString(it).trim().takeIf(String::isNotBlank) }
            .map(::resolveTopic)
            .distinct().take(100)
        require(selected.isNotEmpty()) { "Add at least one topic." }
        prefs.edit().putString(TOPICS_KEY, JSONArray(selected).toString()).apply()
        return selected
    }

    fun asJson(prefs: SharedPreferences): JSONObject {
        val topicArray = JSONArray(allTopics(prefs))
        val sourceArray = JSONArray().apply {
            sources(prefs).forEach {
                put(JSONObject().put("kind", it.kind).put("id", it.id).put("name", it.name)
                    .put("productive", it.productive))
            }
        }
        val snoozedArray = JSONArray().apply {
            snoozedTopics(prefs).forEach { (name, until) ->
                put(JSONObject().put("name", name).put("until", until))
            }
        }
        return JSONObject()
            .put("topics", topicArray)
            .put("sources", sourceArray)
            .put("topicModes", JSONObject(topicModes(prefs)))
            .put("snoozedTopics", snoozedArray)
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
