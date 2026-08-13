package org.sovereignhq.sleepwave

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sovereignhq.sleepwave.audio.EventDetector
import org.sovereignhq.sleepwave.audio.NightRecorder
import org.sovereignhq.sleepwave.data.EventKind

/**
 * The classifier is the part most likely to be quietly wrong, and the part hardest to check on a
 * phone: verifying it there means going to sleep and hoping. So the shapes it is supposed to
 * recognise are synthesised here instead - a snore swells and repeats, a fart is short and sudden,
 * a voice sits in the speech bands, a thump is over almost immediately.
 */
class EventDetectorTest {

    private val collected = mutableListOf<EventDetector.Event>()

    private fun detector(triggerDb: Float = 12f) = EventDetector(triggerDb) { event ->
        collected += event
    }

    @Test
    fun `a short sudden low burst is a rumble, not a snore`() {
        val det = detector()
        emitBurst(det, startMs = 0L, durationMs = 700L, profile = LOW, peakAt = 0.08f)
        drain(det, fromMs = 2_000L)

        assertEquals(1, collected.size)
        assertEquals(EventKind.RUMBLE, collected.single().kind)
    }

    @Test
    fun `low bursts repeating on a breathing rhythm are snoring`() {
        val det = detector()
        // Three swelling low bursts, three seconds apart: inhale, inhale, inhale.
        var t = 0L
        repeat(3) {
            emitBurst(det, startMs = t, durationMs = 1_600L, profile = LOW, peakAt = 0.5f)
            t += 3_000L
        }
        drain(det, fromMs = t)

        assertEquals(3, collected.size)
        assertTrue(
            "Expected snoring, got ${collected.map { it.kind }}",
            collected.all { it.kind == EventKind.SNORE }
        )
        assertTrue("The third burst should be inside a confirmed rhythm", collected.last().cadenced)
    }

    @Test
    fun `speech-shaped sound is classified as talking`() {
        val det = detector()
        emitBurst(det, startMs = 0L, durationMs = 1_200L, profile = VOICE, peakAt = 0.4f)
        drain(det, fromMs = 3_000L)

        assertEquals(1, collected.size)
        assertEquals(EventKind.VOICE, collected.single().kind)
    }

    @Test
    fun `a very short broadband hit is a thump`() {
        val det = detector()
        emitBurst(det, startMs = 0L, durationMs = 220L, profile = BROADBAND, peakAt = 0.05f)
        drain(det, fromMs = 2_000L)

        assertEquals(1, collected.size)
        assertEquals(EventKind.THUMP, collected.single().kind)
    }

    @Test
    fun `sound below the trigger is ignored entirely`() {
        val det = detector(triggerDb = 16f)
        // Loud enough to hear, not loud enough to matter at this sensitivity.
        emitBurst(det, startMs = 0L, durationMs = 1_000L, profile = LOW, peakAt = 0.5f, aboveDb = 9f)
        drain(det, fromMs = 3_000L)

        assertTrue("Nothing should be reported, got ${collected.map { it.kind }}", collected.isEmpty())
    }

    @Test
    fun `a higher sensitivity threshold catches what a lower one misses`() {
        val loose = mutableListOf<EventDetector.Event>()
        val strict = mutableListOf<EventDetector.Event>()

        val looseDet = EventDetector(12f) { loose += it }
        val strictDet = EventDetector(22f) { strict += it }

        listOf(looseDet, strictDet).forEach { det ->
            emitBurst(det, startMs = 0L, durationMs = 800L, profile = LOW, peakAt = 0.1f, aboveDb = 15f)
            drain(det, fromMs = 3_000L)
        }

        assertEquals("Loose setting should catch the quiet rumble", 1, loose.size)
        assertTrue("Strict setting should ignore it", strict.isEmpty())
    }

    // ---- frame synthesis ----

    /** low, mid, high band energies for each sound shape. */
    private data class Profile(val low: Float, val mid: Float, val high: Float)

    private val LOW = Profile(low = 12f, mid = 1f, high = 0.6f)
    private val VOICE = Profile(low = 1f, mid = 9f, high = 4f)
    private val BROADBAND = Profile(low = 2.2f, mid = 2f, high = 2f)

    /**
     * Feeds one burst followed by enough silence to close it. Frames are 32 ms, matching the real
     * recorder, and [peakAt] places the loudest frame - early for a hit, mid-burst for a swell.
     */
    private fun emitBurst(
        detector: EventDetector,
        startMs: Long,
        durationMs: Long,
        profile: Profile,
        peakAt: Float,
        aboveDb: Float = 18f
    ) {
        val frames = (durationMs / FRAME_MS).toInt().coerceAtLeast(2) + 1
        val peakFrame = (frames * peakAt).toInt().coerceIn(0, frames - 1)

        for (i in 0 until frames) {
            val above = if (i == peakFrame) aboveDb + 4f else aboveDb
            detector.onFrame(frame(startMs + i * FRAME_MS, above, profile))
        }
        // Silence closes the burst: the detector needs six consecutive quiet frames.
        for (i in 0 until 8) {
            detector.onFrame(frame(startMs + (frames + i) * FRAME_MS, 0f, profile))
        }
    }

    /** Quiet frames, so cadence tracking can expire between test cases. */
    private fun drain(detector: EventDetector, fromMs: Long) {
        for (i in 0 until 10) {
            detector.onFrame(frame(fromMs + i * FRAME_MS, 0f, LOW))
        }
    }

    private fun frame(timeMs: Long, aboveFloorDb: Float, profile: Profile) = NightRecorder.Frame(
        timeMs = timeMs,
        rmsDb = FLOOR_DB + aboveFloorDb,
        floorDb = FLOOR_DB,
        lowEnergy = profile.low,
        midEnergy = profile.mid,
        highEnergy = profile.high
    )

    private companion object {
        const val FRAME_MS = 32L
        const val FLOOR_DB = -60f
    }
}
