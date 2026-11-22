package com.rush.app

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Device-local media library shared by the WebView interface and native playback.
 * No account or YouTube subscription state is modified.
 */
object LocalLibrary {
    private const val SAVED_KEY = "saved"
    private const val HISTORY_KEY = "history_order"
    private const val LEGACY_HISTORY_KEY = "history"
    private const val METADATA_KEY = "item_metadata"

    fun rememberPlayback(
        prefs: SharedPreferences,
        id: String,
        title: String,
        creator: String,
        isMusic: Boolean,
        thumbnail: String = "",
        sourceId: String = id
    ) {
        val cleanId = id.trim().take(256)
        if (cleanId.isBlank()) return
        saveMetadata(prefs, cleanId, title, creator, isMusic, thumbnail, sourceId)
        val history = history(prefs).filterNot { it == cleanId }
        val updated = (listOf(cleanId) + history).take(200)
        prefs.edit()
            .putString(HISTORY_KEY, JSONArray(updated).toString())
            .putStringSet(LEGACY_HISTORY_KEY, updated.toSet())
            .apply()
    }

    fun save(
        prefs: SharedPreferences,
        id: String,
        title: String,
        creator: String,
        isMusic: Boolean,
        thumbnail: String = "",
        sourceId: String = id
    ) {
        val cleanId = id.trim().take(256)
        require(cleanId.isNotBlank()) { "A media ID is required." }
        saveMetadata(prefs, cleanId, title, creator, isMusic, thumbnail, sourceId)
        val updated = prefs.getStringSet(SAVED_KEY, emptySet()).orEmpty().toMutableSet()
        updated += cleanId
        prefs.edit().putStringSet(SAVED_KEY, updated).apply()
    }

    fun unsave(prefs: SharedPreferences, id: String) {
        val cleanId = id.trim()
        if (cleanId.isBlank()) return
        val updated = prefs.getStringSet(SAVED_KEY, emptySet()).orEmpty() - cleanId
        prefs.edit().putStringSet(SAVED_KEY, updated).apply()
    }

    fun removeFromHistory(prefs: SharedPreferences, id: String) {
        val cleanId = id.trim()
        if (cleanId.isBlank()) return
        val updated = history(prefs).filterNot { it == cleanId }
        prefs.edit()
            .putString(HISTORY_KEY, JSONArray(updated).toString())
            .putStringSet(LEGACY_HISTORY_KEY, updated.toSet())
            .apply()
    }

    fun clearHistory(prefs: SharedPreferences) {
        prefs.edit()
            .putString(HISTORY_KEY, "[]")
            .putStringSet(LEGACY_HISTORY_KEY, emptySet())
            .apply()
    }

    fun load(prefs: SharedPreferences): JSONObject {
        val items = runCatching {
            JSONObject(prefs.getString(METADATA_KEY, "{}") ?: "{}")
        }.getOrDefault(JSONObject())
        val playlists = JSONArray().apply {
            PlaylistStore.load(prefs).forEach { playlist ->
                put(JSONObject()
                    .put("id", playlist.id)
                    .put("name", playlist.name)
                    .put("items", JSONArray(playlist.itemIds)))
            }
        }
        return JSONObject()
            .put("saved", JSONArray(prefs.getStringSet(SAVED_KEY, emptySet()).orEmpty().sorted()))
            .put("history", JSONArray(history(prefs)))
            .put("queue", JSONArray(QueueStore.load(prefs)))
            .put("playlists", playlists)
            .put("items", items)
    }

    private fun history(prefs: SharedPreferences): List<String> {
        val stored = runCatching {
            JSONArray(prefs.getString(HISTORY_KEY, "[]") ?: "[]")
        }.getOrDefault(JSONArray())
        val ordered = (0 until stored.length())
            .mapNotNull { stored.optString(it).takeIf(String::isNotBlank) }
            .distinct()
        if (ordered.isNotEmpty()) return ordered.take(200)
        return prefs.getStringSet(LEGACY_HISTORY_KEY, emptySet())
            .orEmpty().toList().take(200)
    }

    private fun saveMetadata(
        prefs: SharedPreferences,
        id: String,
        title: String,
        creator: String,
        isMusic: Boolean,
        thumbnail: String,
        sourceId: String
    ) {
        val root = runCatching {
            JSONObject(prefs.getString(METADATA_KEY, "{}") ?: "{}")
        }.getOrDefault(JSONObject())
        val existing = root.optJSONObject(id) ?: JSONObject()
        root.put(id, existing
            .put("title", title.trim().take(300))
            .put("creator", creator.trim().take(200))
            .put("music", isMusic)
            .put("thumbnail", thumbnail.take(2048))
            .put("sourceId", sourceId.trim().take(256)))
        prefs.edit().putString(METADATA_KEY, root.toString()).apply()
    }
}
