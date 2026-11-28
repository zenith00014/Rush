package com.rush.app.ui.screens.settings.index

import com.rush.app.R
import com.rush.app.ui.components.settings.SettingAvailability
import com.rush.app.ui.components.settings.SettingEntry
import com.rush.app.ui.components.settings.SettingsDestination

internal object ProfilesIndex {
    val add =
        SettingEntry(
            key = "profiles.add",
            title = R.string.profiles_add,
            summary = R.string.profiles_add_summary,
            keywords = R.string.profiles_keywords,
            destination = SettingsDestination.PROFILES,
        )

    /** TV only: a TV is where a profile list is most often shared. */
    val askWhosWatching =
        SettingEntry(
            key = "profiles.ask_whos_watching",
            title = R.string.profiles_ask_whos_watching,
            summary = R.string.profiles_ask_whos_watching_summary,
            keywords = R.string.profiles_keywords,
            destination = SettingsDestination.PROFILES,
            availability = SettingAvailability.TvOnly,
        )

    /** Only the list is searchable: the editor needs a profile to open. */
    val all = listOf(add, askWhosWatching)
}
