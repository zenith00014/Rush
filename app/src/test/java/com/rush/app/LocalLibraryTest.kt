package com.rush.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalLibraryTest {
    private lateinit var prefs: android.content.SharedPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = context.getSharedPreferences("rush-test-library", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun savesItemsAndDeduplicatesPlaybackHistory() {
        LocalLibrary.save(
            prefs,
            id = "video000001",
            title = "Saved video",
            creator = "Creator",
            isMusic = false,
            thumbnail = "https://i.ytimg.com/vi/video000001/hqdefault.jpg"
        )
        LocalLibrary.rememberPlayback(prefs, "track000001", "Track", "Artist", true)
        LocalLibrary.rememberPlayback(prefs, "video000001", "Saved video", "Creator", false)
        LocalLibrary.rememberPlayback(prefs, "track000001", "Track", "Artist", true)

        val data = LocalLibrary.load(prefs)
        assertEquals(1, data.getJSONArray("saved").length())
        assertEquals("track000001", data.getJSONArray("history").getString(0))
        assertEquals(2, data.getJSONArray("history").length())
        assertEquals("Track", data.getJSONObject("items").getJSONObject("track000001").getString("title"))
    }

    @Test
    fun queueAndPlaylistsPersistAcrossLoads() {
        LocalLibrary.rememberItem(prefs, "video000001", "One", "Creator", false)
        LocalLibrary.rememberItem(prefs, "track000001", "Two", "Artist", true)
        QueueStore.add(prefs, "video000001")
        QueueStore.add(prefs, "track000001")
        QueueStore.move(prefs, 1, 0)

        val playlist = PlaylistStore.create(prefs, "Mixed media")
        PlaylistStore.add(prefs, playlist.first().id, "video000001")
        PlaylistStore.add(prefs, playlist.first().id, "track000001")

        val data = LocalLibrary.load(prefs)
        assertEquals("track000001", data.getJSONArray("queue").getString(0))
        assertEquals(1, data.getJSONArray("playlists").length())
        assertEquals(2, data.getJSONArray("playlists").getJSONObject(0).getJSONArray("items").length())
    }

    @Test
    fun removingHistoryDoesNotRemoveSavedItems() {
        LocalLibrary.save(prefs, "video000001", "One", "Creator", false)
        LocalLibrary.rememberPlayback(prefs, "video000001", "One", "Creator", false)
        LocalLibrary.removeFromHistory(prefs, "video000001")

        val data = LocalLibrary.load(prefs)
        assertEquals(0, data.getJSONArray("history").length())
        assertEquals(1, data.getJSONArray("saved").length())
        LocalLibrary.clearHistory(prefs)
        assertFalse(LocalLibrary.load(prefs).getJSONArray("saved").isEmpty())
    }

    @Test
    fun topicSelectionIsValidatedAndChannelRecommendationIsExplicit() {
        FeedPreferences.saveTopics(prefs, """["Science","Programming"]""")
        FeedPreferences.follow(prefs, "channel", "UC0123456789", "Science channel", false)
        var source = FeedPreferences.sources(prefs).single()
        assertFalse(source.productive)

        FeedPreferences.setProductive(prefs, "UC0123456789", "Science channel", true)
        source = FeedPreferences.sources(prefs).single()
        assertTrue(source.productive)
        assertEquals(listOf("Science", "Programming"), FeedPreferences.topics(prefs))

        var rejected = false
        try {
            FeedPreferences.saveTopics(prefs, """["not-a-topic"]""")
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("Unknown topics must be rejected", rejected)
    }
}
