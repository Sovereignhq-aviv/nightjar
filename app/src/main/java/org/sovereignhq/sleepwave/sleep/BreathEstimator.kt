package org.sovereignhq.sleepwave.sleep

import org.sovereignhq.sleepwave.audio.NightRecorder
import kotlin.math.sqrt

/**
 * Measures breathing from the low-frequency envelope of the room.
 *
 * This is the signal the first version of the app was missing entirely, and it is worth more than
 * any amount of threshold tuning: breathing rate and - more importantly - how *steady* it is
 * separate the sleep stages in a way loudness alone cannot. Deep sleep is metronomic. REM is
 * irregular while the body stays still, which is exactly the combination that used to be
 * indistinguishable from deep sleep.
 *
 * No machine learning here, just autocorrelation: a periodic signal correlates with a delayed copy
 * of itself, and the delay that correlates best is the breathing period. The strength of that
 * correlation is the regularity.
 */
class BreathEstimator {

    private val envelope = FloatArray(BUFFER)
    private var written = 0
    private var frameCounter = 0
    private var accumulator = 0f
    private var accumulated = 0

    /** Feed every audio frame; the estimator does its own downsampling. */
    fun onFrame(frame: NightRecorder.Frame) {
        // Amplitude rather than energy: better dynamic range for a quiet, slow signal.
        accumulator += sqrt(frame.lowEnergy)
        accumulated++
        frameCounter++
        if (frameCounter < FRAMES_PER_SAMPLE) return

        val value = if (accumulated > 0) accumulator / accumulated else 0f
        envelope[written % BUFFER] = value
        written++
        frameCounter = 0
        accumulator = 0f
        accumulated = 0
    }

    /**
     * Breaths per minute and a 0..1 regularity score for the stretch just gone.
     * Returns [Breathing.NONE] when there is not enough signal to be honest about.
     */
    fun estimate(): Breathing {
        val available = minOf(written, BUFFER)
        if (available < MIN_SAMPLES) return Breathing.NONE

        // Oldest-to-newest, mean removed: autocorrelation needs a zero-centred signal or the DC
        // component swamps every lag equally.
        val start = if (written < BUFFER) 0 else written % BUFFER
        val signal = FloatArray(available) { envelope[(start + it) % BUFFER] }

        val mean = signal.average().toFloat()
        for (i in signal.indices) signal[i] -= mean

        val energy = signal.sumOf { (it * it).toDouble() }
        if (energy < MIN_ENERGY) return Breathing.NONE

        var bestLag = -1
        var bestScore = 0f
        for (lag in MIN_LAG..MAX_LAG) {
            if (lag >= available) break
            var dot = 0.0
            var normA = 0.0
            var normB = 0.0
            for (i in 0 until available - lag) {
                val a = signal[i]
                val b = signal[i + lag]
                dot += (a * b).toDouble()
                normA += (a * a).toDouble()
                normB += (b * b).toDouble()
            }
            val denominator = sqrt(normA * normB)
            if (denominator <= 0.0) continue
            val score = (dot / denominator).toFloat()
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }

        if (bestLag < 0 || bestScore < MIN_CORRELATION) return Breathing.NONE

        val periodSeconds = bestLag / SAMPLES_PER_SECOND
        val rate = 60f / periodSeconds
        return Breathing(
            ratePerMinute = rate,
            regularity = bestScore.coerceIn(0f, 1f)
        )
    }

    data class Breathing(val ratePerMinute: Float, val regularity: Float) {
        val detected: Boolean get() = ratePerMinute > 0f

        companion object {
            val NONE = Breathing(0f, 0f)
        }
    }

    private companion object {
        /** Audio frames are 32 ms, so averaging three gives a ~10.4 Hz envelope. */
        const val FRAMES_PER_SAMPLE = 3
        val SAMPLES_PER_SECOND = 1000f / (NightRecorder.FRAME_MS * FRAMES_PER_SAMPLE)

        /** Two minutes of envelope, so a slow breather still gets many cycles to correlate. */
        const val BUFFER = 1_250

        /** Human sleeping breathing: 6 to 30 per minute, so periods of 2 to 10 seconds. */
        val MIN_LAG = (SAMPLES_PER_SECOND * 2f).toInt()
        val MAX_LAG = (SAMPLES_PER_SECOND * 10f).toInt()

        /** Need at least twice the longest period to claim a rate at all. */
        val MIN_SAMPLES = MAX_LAG * 2

        const val MIN_ENERGY = 1e-8
        /** Below this the "period" found is noise, not a breath. */
        const val MIN_CORRELATION = 0.30f
    }
}
