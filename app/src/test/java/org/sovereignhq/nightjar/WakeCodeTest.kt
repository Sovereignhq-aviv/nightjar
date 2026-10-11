package org.sovereignhq.nightjar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sovereignhq.nightjar.alarm.WakeCode

class WakeCodeTest {

    @Test
    fun theRegisteredCodeMatches() {
        assertTrue(WakeCode.matches("7290012345678", "7290012345678"))
    }

    @Test
    fun aTrailingNewlineFromTheScannerStillMatches() {
        assertTrue(WakeCode.matches("bathroom\n", "bathroom"))
    }

    @Test
    fun aDifferentCodeDoesNot() {
        assertFalse(WakeCode.matches("7290012345679", "7290012345678"))
    }

    @Test
    fun nothingRegisteredMeansNothingMatches() {
        assertFalse(WakeCode.matches("", ""))
        assertFalse(WakeCode.matches("anything", "  "))
    }

    @Test
    fun longCodesAreShortenedForDisplay() {
        assertEquals("https://example.org/a...", WakeCode.preview("https://example.org/a/very/long/path"))
        assertEquals("short", WakeCode.preview(" short "))
    }
}
