package com.rush.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RushPlaybackEngineTest {
    @Test
    fun autoSelectsHighestAvailableDirectQuality() {
        assertEquals(1440, selectVideoHeight(listOf(360, 720, 1080, 1440), null))
    }

    @Test
    fun qualityCapChoosesBestAvailableFormatAtOrBelowTheCap() {
        assertEquals(720, selectVideoHeight(listOf(360, 480, 720, 1080, 2160), 900))
    }

    @Test
    fun ultraHdCapUsesHighestDirectFormatWhenSourceDoesNotOffer4k() {
        assertEquals(1080, selectVideoHeight(listOf(360, 720, 1080), 2160))
    }

    @Test
    fun lowestAvailableFormatIsUsedWhenEverySourceFormatExceedsTheCap() {
        assertEquals(720, selectVideoHeight(listOf(720, 1080), 480))
    }

    @Test
    fun ignoresInvalidHeightsAndReturnsNullWhenNoUsableFormatExists() {
        assertNull(selectVideoHeight(listOf(0, -1), 2160))
    }
}
