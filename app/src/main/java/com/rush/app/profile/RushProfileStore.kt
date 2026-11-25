package com.rush.app.profile

import android.content.Context
import org.json.JSONObject

/**
 * Stores the optional Rush identity locally. Rush does not create an account
 * or upload this profile; it is used to personalize the on-device experience.
 */
object RushProfileStore {
    private const val PREFS = "rush_user_profile"
    private const val KEY_PROFILE = "profile_v1"

    fun read(context: Context): JSONObject {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PROFILE, null)
        return runCatching { raw?.let(::JSONObject) ?: JSONObject() }
            .getOrDefault(JSONObject())
    }

    fun save(context: Context, displayName: String, handle: String): Boolean {
        val name = displayName.trim().take(40)
        if (name.isBlank()) return false

        val normalizedHandle = handle.trim()
            .removePrefix("@")
            .filter { it.isLetterOrDigit() || it == '_' || it == '.' }
            .take(24)

        val previous = read(context)
        val profile = JSONObject()
            .put("displayName", name)
            .put("handle", normalizedHandle)
            .put("createdAt", previous.optLong("createdAt", System.currentTimeMillis()))
            .put("updatedAt", System.currentTimeMillis())
            .put("profileComplete", true)

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROFILE, profile.toString())
            .apply()
        return true
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PROFILE)
            .apply()
    }
}
