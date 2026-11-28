package com.rush.app.updater

import android.content.Intent
import com.rush.app.data.update.AppRelease
import com.rush.app.data.update.InstallPhase
import com.rush.app.data.update.UpdateDownload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/** Foss builds never install updates themselves; their store does. */
@Singleton
class AppUpdateInstaller
    @Inject
    constructor() {
        val installPhase: StateFlow<InstallPhase> = MutableStateFlow(InstallPhase.Idle)

        val isAvailable: Boolean = false

        fun download(release: AppRelease) = Unit

        fun cancel() = Unit

        fun state(version: String): Flow<UpdateDownload> = flowOf(UpdateDownload.Idle)

        fun canInstall(): Boolean = false

        fun installPermissionIntent(): Intent = Intent()

        suspend fun install(version: String) = Unit

        fun reconcile() = Unit

        fun clean(keepVersion: String?) = Unit
    }
