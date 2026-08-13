package org.sovereignhq.sleepwave.sleep

import org.sovereignhq.sleepwave.data.Sample
import org.sovereignhq.sleepwave.data.SessionStats
import org.sovereignhq.sleepwave.data.SleepSession
import org.sovereignhq.sleepwave.data.Stage
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Turns a night of per-minute restlessness into awake / light / deep.
 *
 * Everything is scaled against the night's own quiet and busy levels rather than fixed dB
 * numbers, because a phone on a mattress in a city flat and one on a nightstand in the
 * countryside produce completely different absolute values for identical sleep.
 *
 * This is a heuristic, not a trained model, and it is honest about that: it separates
 * "still" from "restless" reliably, and calls the still stretches deep sleep. It cannot see
 * REM, which lives inside LIGHT.
 */
object SleepClassifier {

    fun classify(session: SleepSession): List<Sample> {
        val raw = session.samples
        if (raw.isEmpty()) return raw
        if (raw.size < MIN_MINUTES) {
            return raw.map { it.copy(stage = Stage.AWAKE.ordinal) }
        }

        val activity = FloatArray(raw.size) { raw[it].activity }
        val smooth = movingAverage(activity, 5)

        val quiet = percentile(smooth, 0.10f)
        val busy = percentile(smooth, 0.85f)
        val span = busy - quiet

        val normalised = FloatArray(smooth.size) { i ->
            val base = if (span > 0.04f) ((smooth[i] - quiet) / span) else smooth[i].coerceIn(0f, 1f)
            // Sleep-pressure prior: deep sleep concentrates in the first hours, and the last
            // stretch before waking is mostly light. A gentle nudge, never a decision on its own.
            val earlyDeepBias = 0.10f * exp(-i / 150f)
            val lateLightBias = 0.07f * (i.toFloat() / smooth.size)
            (base - earlyDeepBias + lateLightBias).coerceIn(0f, 1f)
        }

        var stages = IntArray(normalised.size) { i ->
            when {
                normalised[i] > AWAKE_LEVEL -> Stage.AWAKE.ordinal
                normalised[i] > LIGHT_LEVEL -> Stage.LIGHT.ordinal
                else -> Stage.DEEP.ordinal
            }
        }

        stages = medianFilter(stages, 5)
        enforceMinimumRuns(stages)
        markSleepOnset(stages, normalised)

        return raw.mapIndexed { i, sample -> sample.copy(stage = stages[i]) }
    }

    fun stats(session: SleepSession, goalMinutes: Int): SessionStats {
        val samples = if (session.samples.any { it.stage != Stage.LIGHT.ordinal }) {
            session.samples
        } else {
            classify(session)
        }

        val total = samples.size
        val deep = samples.count { it.stage == Stage.DEEP.ordinal }
        val light = samples.count { it.stage == Stage.LIGHT.ordinal }
        val awake = samples.count { it.stage == Stage.AWAKE.ordinal }
        val asleep = deep + light

        val onset = samples.indexOfFirst { it.stage != Stage.AWAKE.ordinal }.let { if (it < 0) total else it }

        // An awakening is an awake run that starts after you first fell asleep and lasts a
        // few minutes - long enough that you would remember it.
        var awakenings = 0
        var run = 0
        for (i in onset until total) {
            if (samples[i].stage == Stage.AWAKE.ordinal) {
                run++
            } else {
                if (run >= AWAKENING_MINUTES) awakenings++
                run = 0
            }
        }
        if (run >= AWAKENING_MINUTES) awakenings++

        val efficiency = if (total > 0) (asleep * 100f / total).roundToInt() else 0

        val durationScore = if (goalMinutes > 0) (asleep.toFloat() / goalMinutes).coerceIn(0f, 1f) else 0f
        val deepScore = if (asleep > 0) ((deep.toFloat() / asleep) / IDEAL_DEEP_FRACTION).coerceIn(0f, 1f) else 0f
        val continuityScore = (1f - (awakenings / 6f)).coerceIn(0f, 1f)
        val efficiencyScore = (efficiency / 100f).coerceIn(0f, 1f)

        val score = (100f * (
            0.40f * durationScore +
                0.25f * deepScore +
                0.20f * continuityScore +
                0.15f * efficiencyScore
            )).roundToInt().coerceIn(0, 100)

        return SessionStats(
            totalMinutes = total,
            asleepMinutes = asleep,
            deepMinutes = deep,
            lightMinutes = light,
            awakeMinutes = awake,
            awakenings = awakenings,
            sleepOnsetMinutes = onset,
            efficiencyPct = efficiency,
            score = score,
            snoreMinutes = session.snoreMinutes
        )
    }

    /**
     * The night's own quiet and busy levels, which is what every threshold in the app is
     * measured against. Exposed so the live smart alarm scales exactly like the morning report.
     */
    fun quietAndBusy(activity: List<Float>): Pair<Float, Float> {
        if (activity.isEmpty()) return 0f to 1f
        val smooth = movingAverage(activity.toFloatArray(), 5)
        return percentile(smooth, 0.10f) to percentile(smooth, 0.85f)
    }

    // ---- helpers ----

    private fun movingAverage(values: FloatArray, window: Int): FloatArray {
        val half = window / 2
        return FloatArray(values.size) { i ->
            var sum = 0f
            var n = 0
            for (j in (i - half)..(i + half)) {
                if (j in values.indices) {
                    sum += values[j]
                    n++
                }
            }
            if (n > 0) sum / n else values[i]
        }
    }

    private fun percentile(values: FloatArray, p: Float): Float {
        val sorted = values.sorted()
        if (sorted.isEmpty()) return 0f
        val idx = ((sorted.size - 1) * p).roundToInt().coerceIn(0, sorted.size - 1)
        return sorted[idx]
    }

    private fun medianFilter(values: IntArray, window: Int): IntArray {
        val half = window / 2
        val out = IntArray(values.size)
        val buf = IntArray(window)
        for (i in values.indices) {
            var n = 0
            for (j in (i - half)..(i + half)) {
                if (j in values.indices) buf[n++] = values[j]
            }
            val slice = buf.copyOf(n)
            slice.sort()
            out[i] = slice[n / 2]
        }
        return out
    }

    /**
     * Removes physiologically impossible flicker: a single minute of deep sleep between two
     * awake minutes is a measurement artefact, not a sleep stage.
     */
    private fun enforceMinimumRuns(stages: IntArray) {
        var i = 0
        while (i < stages.size) {
            var j = i
            while (j < stages.size && stages[j] == stages[i]) j++
            val length = j - i
            val minimum = when (stages[i]) {
                Stage.DEEP.ordinal -> 4
                Stage.AWAKE.ordinal -> 2
                else -> 1
            }
            if (length < minimum) {
                val before = if (i > 0) stages[i - 1] else -1
                val after = if (j < stages.size) stages[j] else -1
                val replacement = when {
                    before >= 0 && after >= 0 -> if (before == after) before else Stage.LIGHT.ordinal
                    before >= 0 -> before
                    after >= 0 -> after
                    else -> Stage.LIGHT.ordinal
                }
                for (k in i until j) stages[k] = replacement
                // Re-walk from the previous run so merges cascade correctly.
                i = if (i > 0) i - 1 else 0
                while (i > 0 && stages[i - 1] == stages[i]) i--
                continue
            }
            i = j
        }
    }

    /** Before you fall asleep you are awake, even if you were lying very still. */
    private fun markSleepOnset(stages: IntArray, normalised: FloatArray) {
        var settledAt = -1
        var quietRun = 0
        for (i in stages.indices) {
            if (normalised[i] < AWAKE_LEVEL) {
                quietRun++
                if (quietRun >= SETTLE_MINUTES) {
                    settledAt = i - quietRun + 1
                    break
                }
            } else {
                quietRun = 0
            }
        }
        if (settledAt > 0) {
            for (i in 0 until settledAt) stages[i] = Stage.AWAKE.ordinal
        }
    }

    private const val AWAKE_LEVEL = 0.60f
    private const val LIGHT_LEVEL = 0.28f
    private const val IDEAL_DEEP_FRACTION = 0.20f
    private const val AWAKENING_MINUTES = 3
    private const val SETTLE_MINUTES = 5
    private const val MIN_MINUTES = 20
}
