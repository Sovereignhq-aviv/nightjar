package org.sovereignhq.sleepwave

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sovereignhq.sleepwave.audio.Fft
import org.sovereignhq.sleepwave.audio.WavWriter
import org.sovereignhq.sleepwave.data.Sample
import org.sovereignhq.sleepwave.data.SleepSession
import org.sovereignhq.sleepwave.data.Stage
import org.sovereignhq.sleepwave.sleep.SleepClassifier
import org.sovereignhq.sleepwave.sleep.SmartAlarm
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Covers the parts that decide whether the app is right or wrong and that can be checked without a
 * phone: the spectrum analysis, the sleep staging, and the wake-up decision.
 */
class SleepEngineTest {

    @Test
    fun `fft finds the frequency it was given`() {
        val size = 512
        val sampleRate = 16_000
        val toneHz = 250.0
        val samples = FloatArray(size) { i -> sin(2.0 * PI * toneHz * i / sampleRate).toFloat() }

        val mags = FloatArray(size / 2)
        Fft(size).magnitudes(samples, 0, mags)

        val peakBin = mags.indices.maxByOrNull { mags[it] } ?: -1
        val peakHz = peakBin * sampleRate.toDouble() / size
        // Bin resolution is 31.25 Hz, so landing within one bin of 250 Hz is exact.
        assertTrue("Peak landed at $peakHz Hz", abs(peakHz - toneHz) <= 31.25)
    }

    @Test
    fun `a still night is mostly deep sleep and a restless one is not`() {
        val quiet = Random(1)
        val busy = Random(2)
        val still = sessionFrom(FloatArray(420) { 0.05f + quiet.nextFloat() * 0.03f })
        val restless = sessionFrom(FloatArray(420) { 0.55f + busy.nextFloat() * 0.2f })

        val stillStats = SleepClassifier.stats(still, goalMinutes = 480)
        val restlessStats = SleepClassifier.stats(restless, goalMinutes = 480)

        assertTrue(
            "Still night should score higher (${stillStats.score} vs ${restlessStats.score})",
            stillStats.score > restlessStats.score
        )
        assertTrue("Still night should contain deep sleep", stillStats.deepMinutes > 0)
    }

    @Test
    fun `awake stretches at the start count as falling asleep, not as sleep`() {
        val activity = FloatArray(400) { i -> if (i < 20) 0.9f else 0.08f }
        val stats = SleepClassifier.stats(sessionFrom(activity), goalMinutes = 480)

        assertTrue(
            "Expected roughly 20 minutes to fall asleep, got ${stats.sleepOnsetMinutes}",
            stats.sleepOnsetMinutes in 10..30
        )
    }

    @Test
    fun `single-minute stage flickers are smoothed away`() {
        val activity = FloatArray(300) { i -> if (i == 150) 0.95f else 0.06f }
        val stages = SleepClassifier.classify(sessionFrom(activity)).map { it.stage }

        var run = 0
        var oneMinuteBlips = 0
        (stages + Stage.DEEP.ordinal).forEach { stage ->
            if (stage == Stage.AWAKE.ordinal) run++ else {
                if (run == 1) oneMinuteBlips++
                run = 0
            }
        }
        assertEquals("No one-minute awake blips expected", 0, oneMinuteBlips)
    }

    @Test
    fun `the smart alarm always rings by the end of its window`() {
        val decision = SmartAlarm.evaluate(
            minutesIntoWindow = 30,
            windowMinutes = 30,
            recent = List(10) { 0.01f },
            nightQuiet = 0.0f,
            nightBusy = 1.0f
        )
        assertTrue("Must fire on the deadline", decision.wake)
        assertTrue("Deadline wake is not a smart wake", !decision.smart)
    }

    @Test
    fun `the smart alarm waits while you are deeply asleep`() {
        val decision = SmartAlarm.evaluate(
            minutesIntoWindow = 2,
            windowMinutes = 30,
            recent = List(10) { 0.02f },
            nightQuiet = 0.0f,
            nightBusy = 1.0f
        )
        assertTrue("Should not wake someone in deep sleep early in the window", !decision.wake)
    }

    @Test
    fun `the smart alarm catches you stirring inside the window`() {
        val recent = listOf(0.03f, 0.04f, 0.03f, 0.05f, 0.04f, 0.03f, 0.62f, 0.70f, 0.68f)
        val decision = SmartAlarm.evaluate(
            minutesIntoWindow = 10,
            windowMinutes = 30,
            recent = recent,
            nightQuiet = 0.0f,
            nightBusy = 1.0f
        )
        assertTrue("Should wake on a clear stir", decision.wake)
        assertTrue("A stir wake is a smart wake", decision.smart)
    }

    @Test
    fun `wav files carry a valid 44 byte header`() {
        val file = File.createTempFile("sleepwave", ".wav")
        val pcm = ShortArray(1_600) { (it % 300 - 150).toShort() }
        WavWriter.write(file, pcm, pcm.size, 16_000)

        val bytes = file.readBytes()
        assertEquals("RIFF", String(bytes, 0, 4))
        assertEquals("WAVE", String(bytes, 8, 4))
        assertEquals("data", String(bytes, 36, 4))
        assertEquals("Header plus 2 bytes per sample", 44 + pcm.size * 2, bytes.size)
        file.delete()
    }

    private fun sessionFrom(activity: FloatArray): SleepSession = SleepSession(
        id = "test",
        startedAtMs = 0L,
        endedAtMs = activity.size * 60_000L,
        alarmTargetMs = activity.size * 60_000L,
        windowMinutes = 30,
        samples = activity.mapIndexed { i, a -> Sample(minute = i, activity = a, loudnessDb = a * 30f) }
    )
}
