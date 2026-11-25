package com.rush.app.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject

/**
 * Reports only permissions Rush actually uses. Notification permission is
 * requested contextually; playback and app-private downloads need no storage,
 * camera, microphone, contacts, or location permission.
 */
object RushPermissionManager {
    fun notificationState(context: Context): JSONObject {
        val runtimePermissionRequired = Build.VERSION.SDK_INT >= 33
        val runtimeGranted = !runtimePermissionRequired ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        return JSONObject()
            .put("notificationRuntimePermissionRequired", runtimePermissionRequired)
            .put("notificationRuntimePermissionGranted", runtimeGranted)
            .put("notificationsEnabled", runtimeGranted && notificationsEnabled)
            .put("notificationsBlockedInSystemSettings", runtimeGranted && !notificationsEnabled)
            .put("storagePermissionRequired", false)
            .put("cameraPermissionRequired", false)
            .put("microphonePermissionRequired", false)
            .put("locationPermissionRequired", false)
    }
}
