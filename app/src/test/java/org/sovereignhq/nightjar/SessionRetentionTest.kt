package org.sovereignhq.nightjar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sovereignhq.nightjar.data.SleepSession
import org.sovereignhq.nightjar.data.SoundClip

/**
 * Night retention against starring.
 *
 * Worth its own test because the two retention tiers pull in opposite directions. Audio retention
 * already spares starred clips, but night retention used to drop a whole night with its audio, so
 * a starred clip quietly disappeared once its night aged out - after as little as thirty days, the
 * lowest the slider goes. That is the one deletion a listener would never expect, and it
 * contradicted both the model docs and the settings screen, which promise starred audio is kept
 * forever. Only a deliberate delete may take it now.
 */
class SessionRetentionTest {

    private fun clip(name: String, starred: Boolean) = SoundClip(
        fileName = name,
        startedAtMs = 0L,
        durationMs = 1_000L,
        kind = "SNORE",
        peakDb = 20f,
        starred = starred
    )

    private fun night(startedAtMs: Long, clips: List<SoundClip>) = SleepSession(
        id = "night-$startedAtMs",
        startedAtMs = startedAtMs,
        endedAtMs = startedAtMs + 8L * 60L * 60L * 1000L,
        alarmTargetMs = startedAtMs,
        windowMinutes = 30,
        clips = clips
    )

    @Test
    fun `an old night holding nothing starred is dropped`() {
        val old = night(1_000L, listOf(clip("a.wav", starred = false)))
        assertTrue(old.isDroppableBefore(5_000L))
    }

    @Test
    fun `one starred clip keeps the whole night`() {
        val old = night(1_000L, listOf(clip("a.wav", starred = false), clip("b.wav", starred = true)))
        assertFalse(old.isDroppableBefore(5_000L))
    }

    @Test
    fun `a starred clip survives however far past the cutoff its night sits`() {
        val ancient = night(0L, listOf(clip("a.wav", starred = true)))
        assertFalse(ancient.isDroppableBefore(Long.MAX_VALUE))
    }

    @Test
    fun `a night newer than the cutoff is never dropped`() {
        val recent = night(9_000L, listOf(clip("a.wav", starred = false)))
        assertFalse(recent.isDroppableBefore(5_000L))
    }

    @Test
    fun `an old night with no audio left is dropped`() {
        assertTrue(night(1_000L, emptyList()).isDroppableBefore(5_000L))
    }
}
