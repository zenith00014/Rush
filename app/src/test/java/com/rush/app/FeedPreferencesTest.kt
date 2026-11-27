package com.rush.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FeedPreferencesTest {
    private lateinit var prefs: android.content.SharedPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = context.getSharedPreferences("rush-test-feed", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun everyFollowedChannelCanSupplyForYouWithoutSeparateOptIn() {
        FeedPreferences.follow(prefs, "channel", "UC00000000001", "First channel", productive = false)
        FeedPreferences.follow(prefs, "channel", "UC00000000002", "Second channel", productive = true)
        runCatching { FeedPreferences.follow(prefs, "artist", "artist-1", "An artist") }
            .onSuccess { throw AssertionError("Non-channel follows must be rejected") }
        runCatching { FeedPreferences.follow(prefs, "news", "publisher-1", "A publisher") }
            .onSuccess { throw AssertionError("Non-channel follows must be rejected") }
        val channels = FeedPreferences.forYouChannels(FeedPreferences.sources(prefs))
        assertEquals(listOf("UC00000000001", "UC00000000002"), channels.map { it.id })
        assertTrue(channels.all { it.kind == "channel" })
    }

    @Test
    fun selectedTopicsComeFromApprovedCatalog() {
        val selected = FeedPreferences.saveTopics(prefs, """["Science","Shorts","Technology"]""")
        assertEquals(listOf("Science", "Technology"), selected)
        assertTrue(FeedPreferences.topicCatalog.containsAll(selected))
        assertTrue("Shorts" !in FeedPreferences.topicCatalog)
    }
}
