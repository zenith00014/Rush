package com.rush.app.ui.screens.settings.content

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rush.app.R
import com.rush.app.data.local.PlaylistSwipeAction
import com.rush.app.ui.components.library.LibrarySection
import com.rush.app.ui.components.library.labelRes
import com.rush.app.ui.components.settings.SettingsPage
import com.rush.app.ui.components.settings.choice
import com.rush.app.ui.components.settings.switch
import com.rush.app.ui.components.shared.FlowChoice
import com.rush.app.ui.components.shared.FlowChoiceDialog
import com.rush.app.ui.screens.settings.index.LibrarySectionsIndex

/**
 * One switch per Library section, laid out as Library lays them out; a hidden section is left out of
 * Library. Below them, what a swipe on a playlist video does.
 */
@Composable
internal fun LibrarySettingsScreen(
    onBack: (() -> Unit)?,
    highlight: String?,
    viewModel: LibrarySettingsViewModel = hiltViewModel(),
) {
    val hidden by viewModel.hidden.collectAsStateWithLifecycle()
    val separatePlaylists by viewModel.separatePlaylists.collectAsStateWithLifecycle()
    val shortsContent by viewModel.shortsContent.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val swipes by viewModel.playlistSwipes.collectAsStateWithLifecycle()
    var swipePicker by remember { mutableStateOf<SwipeSide?>(null) }

    fun enabled(section: LibrarySection): Boolean =
        when (section) {
            LibrarySection.SAVED_SHORTS -> shortsContent
            LibrarySection.NOTES -> notes
            else -> true
        }

    SettingsPage(title = stringResource(R.string.library), onBack = onBack, highlight = highlight) {
        group(key = "library.content", header = R.string.library_section_header) {
            LibrarySection.content(separatePlaylists).forEach { section ->
                switch(
                    LibrarySectionsIndex.entry(section),
                    checked = section !in hidden,
                    onCheckedChange = { viewModel.setShown(section, it) },
                    enabled = enabled(section),
                )
            }
        }
        group(key = "library.settings_data", header = R.string.library_settings_data_header) {
            LibrarySection.settingsAndData.forEach { section ->
                switch(
                    LibrarySectionsIndex.entry(section),
                    checked = section !in hidden,
                    onCheckedChange = { viewModel.setShown(section, it) },
                    enabled = enabled(section),
                )
            }
        }
        group(key = "library.playlists", header = R.string.library_playlists_label) {
            choice(LibrarySectionsIndex.playlistSwipeLeft, onClick = { swipePicker = SwipeSide.LEFT }) {
                stringResource(swipes.left.labelRes)
            }
            choice(LibrarySectionsIndex.playlistSwipeRight, onClick = { swipePicker = SwipeSide.RIGHT }) {
                stringResource(swipes.right.labelRes)
            }
        }
    }

    swipePicker?.let { side ->
        val left = side == SwipeSide.LEFT
        FlowChoiceDialog(
            title = stringResource(if (left) R.string.playlist_swipe_left else R.string.playlist_swipe_right),
            description = stringResource(R.string.playlist_swipe_subtitle),
            options = PlaylistSwipeAction.entries.map { FlowChoice(it, stringResource(it.labelRes)) },
            selected = if (left) swipes.left else swipes.right,
            onSelect = { action -> if (left) viewModel.setPlaylistSwipeLeft(action) else viewModel.setPlaylistSwipeRight(action) },
            onDismiss = { swipePicker = null },
        )
    }
}

private enum class SwipeSide { LEFT, RIGHT }
