package com.rush.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LiveYouTubeSearchTest {
    private fun video(title: String = "A regular video", duration: String = "") =
        YouTubeSearchResult(
            videoId = "abcdefghijk",
            title = title,
            creator = "Example channel",
            duration = duration,
            isMusic = false
        )

    @Test
    fun excludesShortDurations() {
        assertTrue(LiveYouTubeSearch.isShortForm(video(duration = "0:45")))
        assertTrue(LiveYouTubeSearch.isShortForm(video(duration = "1:59")))
        assertTrue(LiveYouTubeSearch.isShortForm(video(duration = "3:00")))
    }

    @Test
    fun excludesShortsTitleMarkersEvenWhenLonger() {
        assertTrue(LiveYouTubeSearch.isShortForm(video(title = "Example #Shorts", duration = "4:20")))
        assertTrue(LiveYouTubeSearch.isShortForm(video(title = "Short video", duration = "5:00")))
    }

    @Test
    fun keepsLongFormVideos() {
        assertFalse(LiveYouTubeSearch.isShortForm(video(duration = "3:01")))
        assertFalse(LiveYouTubeSearch.isShortForm(video(duration = "12:45")))
        assertFalse(LiveYouTubeSearch.isShortForm(video(duration = "1:02:30")))
        assertFalse(LiveYouTubeSearch.isShortForm(video(duration = "")))
    }
}
