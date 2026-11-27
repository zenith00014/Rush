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
        FeedPreferences.follow(prefs, "artist", "artist-1", "An artist")
        FeedPreferences.follow(prefs, "news", "publisher-1", "A publisher")
        val channels = FeedPreferences.forYouChannels(FeedPreferences.sources(prefs))
        assertEquals(listOf("UC00000000001", "UC00000000002"), channels.map { it.id })
        assertTrue(channels.all { it.kind == "channel" })
    }

    @Test
    fun onboardingCanSaveTheDefaultLocalInterestSelection() {
        val defaults = FeedPreferences.defaultTopics
        val selected = FeedPreferences.saveOnboardingTopics(prefs, org.json.JSONArray(defaults).toString())

        assertEquals(defaults, selected)
    }

    @Test
    fun onboardingAcceptsDefaultTopicsAndPreviouslySavedCustomInterests() {
        FeedPreferences.addTopic(prefs, "Urban sketching")
        val selected = FeedPreferences.saveOnboardingTopics(
            prefs,
            """["Science","Culture","Language","Urban sketching","Machine learning"]"""
        )

        assertEquals(
            listOf("Science", "Culture", "Language", "Urban sketching", "AI and machine learning"),
            selected
        )
        assertTrue(selected.contains("Urban sketching"))
    }

    @Test
    fun regularTopicSaveStillRejectsUnknownPhrases() {
        var rejected = false
        try {
            FeedPreferences.saveTopics(prefs, """["not-a-topic"]""")
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("Unknown topics must be rejected by regular topic editing", rejected)
    }

    @Test
    fun onboardingRejectsCustomInterestUntilItIsSavedLocally() {
        var rejected = false
        try {
            FeedPreferences.saveOnboardingTopics(prefs, """["not-a-topic"]""")
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("Onboarding must not accept arbitrary unsaved phrases", rejected)
    }

    @Test
    fun freeformTopicAssistMapsAliasesAndKeepsCustomTopics() {
        assertEquals("AI and machine learning", FeedPreferences.addTopic(prefs, "videos about large language models"))
        assertEquals("Urban sketching", FeedPreferences.addTopic(prefs, "urban sketching"))
        assertTrue(FeedPreferences.allTopics(prefs).contains("Urban sketching"))
    }

    @Test
    fun topicModesAndOneDaySnoozeAffectActiveFeedTopics() {
        FeedPreferences.saveTopics(prefs, """["Science","Technology"]""")
        FeedPreferences.setTopicMode(prefs, "Science", "more")
        FeedPreferences.setTopicMode(prefs, "Technology", "hide")
        assertEquals("more", FeedPreferences.topicModes(prefs)["Science"])
        assertTrue("Technology" !in FeedPreferences.topics(prefs))
        val until = FeedPreferences.snoozeTopic(prefs, "Science")
        assertTrue(until - System.currentTimeMillis() in (23L * 60 * 60 * 1000)..(24L * 60 * 60 * 1000))
        assertTrue("Science" !in FeedPreferences.topics(prefs))
        FeedPreferences.unsnoozeTopic(prefs, "Science")
        assertTrue("Science" in FeedPreferences.topics(prefs))
    }
}
