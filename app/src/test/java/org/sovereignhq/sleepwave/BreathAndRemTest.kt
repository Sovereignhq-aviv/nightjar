package org.sovereignhq.sleepwave

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sovereignhq.sleepwave.audio.AudioSetLabels
import org.sovereignhq.sleepwave.audio.NightRecorder
import org.sovereignhq.sleepwave.data.EventKind
import org.sovereignhq.sleepwave.data.Sample
import org.sovereignhq.sleepwave.data.SleepSession
import org.sovereignhq.sleepwave.data.Stage
import org.sovereignhq.sleepwave.sleep.BreathEstimator
import org.sovereignhq.sleepwave.sleep.SleepClassifier
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Breathing measurement and the REM call that depends on it.
 *
 * The REM tests matter more than they look. The dangerous failure is not "misses REM", it is
 * "reports REM every night regardless" — a number that always appears looks like a working feature
 * and is worth nothing. So there is a test for a night that should produce none.
 */
class BreathAndRemTest {

    // ---- breathing ----

    @Test
    fun `a steady synthetic breath is measured at the right rate`() {
        val estimator = BreathEstimator()
        feedBreathing(estimator, breathsPerMinute = 15f, seconds = 120, jitter = 0f)

        val breathing = estimator.estimate()
        assertTrue("Nothing detected at all", breathing.detected)
        assertTrue(
            "Expected about 15/min, got ${breathing.ratePerMinute}",
            abs(breathing.ratePerMinute - 15f) < 2.5f
        )
        assertTrue(
            "A metronomic breath should read as very regular, got ${breathing.regularity}",
            breathing.regularity > 0.6f
        )
    }

    @Test
    fun `a slower breath is distinguished from a faster one`() {
        val slow = BreathEstimator().also { feedBreathing(it, 10f, 120, 0f) }.estimate()
        val fast = BreathEstimator().also { feedBreathing(it, 20f, 120, 0f) }.estimate()

        assertTrue("Both should be detected", slow.detected && fast.detected)
        assertTrue(
            "Expected ${slow.ratePerMinute} < ${fast.ratePerMinute}",
            slow.ratePerMinute < fast.ratePerMinute
        )
    }

    @Test
    fun `irregular breathing reads as less regular than steady breathing`() {
        val steady = BreathEstimator().also { feedBreathing(it, 14f, 120, jitter = 0f) }.estimate()
        val ragged = BreathEstimator().also { feedBreathing(it, 14f, 120, jitter = 0.9f) }.estimate()

        assertTrue("Steady breathing should be detected", steady.detected)
        assertTrue(
            "Ragged (${ragged.regularity}) should score below steady (${steady.regularity})",
            ragged.regularity < steady.regularity
        )
    }

    @Test
    fun `silence produces no breathing reading rather than a made-up one`() {
        val estimator = BreathEstimator()
        repeat(4_000) { i ->
            estimator.onFrame(frame(i * NightRecorder.FRAME_MS, lowEnergy = 0f))
        }
        assertTrue("Silence must not yield a rate", !estimator.estimate().detected)
    }

    // ---- the label map ----

    @Test
    fun `audioset labels land in the right buckets`() {
        assertEquals(EventKind.SNORE, AudioSetLabels.kindFor("Snoring"))
        assertEquals(EventKind.RUMBLE, AudioSetLabels.kindFor("Fart"))
        assertEquals(EventKind.RUMBLE, AudioSetLabels.kindFor("Stomach rumble"))
        assertEquals(EventKind.VOICE, AudioSetLabels.kindFor("Speech"))
        assertEquals(EventKind.VOICE, AudioSetLabels.kindFor("Screaming"))
        assertEquals(EventKind.THUMP, AudioSetLabels.kindFor("Thump, thud"))
        assertEquals(EventKind.OTHER, AudioSetLabels.kindFor("Air conditioning"))
        assertEquals(EventKind.OTHER, AudioSetLabels.kindFor("Cough"))
    }

    @Test
    fun `room tone produces no opinion at all`() {
        // These are what YAMNet reports constantly. Treating them as events would fill the night.
        assertNull(AudioSetLabels.kindFor("Silence"))
        assertNull(AudioSetLabels.kindFor("Inside, small room"))
        assertNull(AudioSetLabels.kindFor("Definitely not a real class"))
    }

    // ---- REM ----

    @Test
    fun `still minutes with irregular breathing become REM`() {
        val session = nightWith(
            minutes = 300,
            irregularRange = 120 until 170
        )
        val stats = SleepClassifier.stats(session, goalMinutes = 480)

        assertTrue("Expected some REM, got ${stats.remMinutes}", stats.remMinutes > 20)
        assertTrue("REM should not swallow the night", stats.remMinutes < 120)

        val stages = SleepClassifier.classify(session)
        assertEquals(
            "The irregular stretch should be REM",
            Stage.REM.ordinal,
            stages[140].stage
        )
    }

    @Test
    fun `a night of perfectly steady breathing reports no REM`() {
        val session = nightWith(minutes = 300, irregularRange = IntRange.EMPTY)
        val stats = SleepClassifier.stats(session, goalMinutes = 480)

        assertEquals(
            "Steady breathing all night must not manufacture REM",
            0,
            stats.remMinutes
        )
    }

    @Test
    fun `no breathing signal means no REM claim`() {
        val samples = (0 until 300).map { i ->
            Sample(minute = i, activity = 0.06f, loudnessDb = 2f, breathRate = 0f, breathRegularity = 0f)
        }
        val stats = SleepClassifier.stats(session(samples), goalMinutes = 480)
        assertEquals("Without breathing data there is nothing to base REM on", 0, stats.remMinutes)
    }

    @Test
    fun `REM counts as sleep, not as time awake`() {
        val session = nightWith(minutes = 300, irregularRange = 120 until 170)
        val stats = SleepClassifier.stats(session, goalMinutes = 480)
        assertEquals(
            "asleep must be light + deep + REM",
            stats.lightMinutes + stats.deepMinutes + stats.remMinutes,
            stats.asleepMinutes
        )
    }

    // ---- helpers ----

    /**
     * A quiet night where [irregularRange] breathed raggedly and everything else breathed steadily.
     * Activity is uniformly low so every minute starts out as deep sleep, which is what REM has to
     * be carved out of.
     */
    private fun nightWith(minutes: Int, irregularRange: IntRange): SleepSession {
        val noise = Random(7)
        val samples = (0 until minutes).map { i ->
            Sample(
                minute = i,
                activity = 0.05f + noise.nextFloat() * 0.02f,
                loudnessDb = 2f,
                breathRate = 14f,
                breathRegularity = if (i in irregularRange) 0.35f else 0.88f
            )
        }
        return session(samples)
    }

    private fun session(samples: List<Sample>) = SleepSession(
        id = "test",
        startedAtMs = 0L,
        endedAtMs = samples.size * 60_000L,
        alarmTargetMs = samples.size * 60_000L,
        windowMinutes = 30,
        samples = samples
    )

    /**
     * Synthesises the low-band envelope of someone breathing: a slow sine at the given rate.
     * [jitter] randomises each cycle's length, which is what real irregular breathing looks like.
     */
    private fun feedBreathing(
        estimator: BreathEstimator,
        breathsPerMinute: Float,
        seconds: Int,
        jitter: Float
    ) {
        val random = Random(11)
        val frames = (seconds * 1000L / NightRecorder.FRAME_MS).toInt()
        var phase = 0.0
        val basePhaseStep = 2.0 * PI * (breathsPerMinute / 60.0) * (NightRecorder.FRAME_MS / 1000.0)

        for (i in 0 until frames) {
            val wobble = if (jitter > 0f) 1.0 + (random.nextDouble() - 0.5) * 2.0 * jitter else 1.0
            phase += basePhaseStep * wobble
            // Amplitude, squared to match the energy units the recorder reports.
            val amplitude = 0.5 + 0.5 * sin(phase)
            estimator.onFrame(
                frame(i * NightRecorder.FRAME_MS, lowEnergy = (amplitude * amplitude).toFloat())
            )
        }
    }

    private fun frame(timeMs: Long, lowEnergy: Float) = NightRecorder.Frame(
        timeMs = timeMs,
        rmsDb = -58f,
        floorDb = -60f,
        lowEnergy = lowEnergy,
        midEnergy = 0.01f,
        highEnergy = 0.01f
    )
}
