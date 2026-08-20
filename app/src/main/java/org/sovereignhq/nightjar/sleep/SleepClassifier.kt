package org.sovereignhq.nightjar.sleep

import org.sovereignhq.nightjar.data.Sample
import org.sovereignhq.nightjar.data.SessionStats
import org.sovereignhq.nightjar.data.SleepSession
import org.sovereignhq.nightjar.data.Stage
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Turns a night of per-minute measurements into awake / light / deep / REM.
 *
 * Everything is scaled against the night's own quiet and busy levels rather than fixed dB numbers,
 * because a phone on a mattress in a city flat and one on a nightstand in the countryside produce
 * completely different absolute values for identical sleep.
 *
 * Two signals, doing different jobs:
 *
 *  - **Movement and sound** separate awake from asleep, and restless from still. Reliable.
 *  - **Breathing regularity** then splits the still minutes. Deep sleep is metronomic; REM is
 *    irregular while the body stays almost motionless. Without a breathing measurement those two
 *    are genuinely indistinguishable, which is why the first version of this app claimed no REM at
 *    all rather than guessing.
 *
 * Still a heuristic, not a trained model - but the REM call is now based on a real physiological
 * signal rather than on nothing.
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
            // Sleep-pressure prior: deep sleep concentrates in the first hours, and the last stretch
            // before waking is mostly light. A gentle nudge, never a decision on its own.
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
        splitRemFromDeep(stages, raw)

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
        val rem = samples.count { it.stage == Stage.REM.ordinal }
        val awake = samples.count { it.stage == Stage.AWAKE.ordinal }
        val asleep = deep + light + rem

        val onset = samples.indexOfFirst { it.stage != Stage.AWAKE.ordinal }
            .let { if (it < 0) total else it }

        // An awakening is an awake run that starts after you first fell asleep and lasts a few
        // minutes - long enough that you would remember it.
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

        val breathing = samples.filter { it.breathRate > 0f }
        val breathRate = if (breathing.isEmpty()) 0f else {
            breathing.map { it.breathRate }.average().toFloat()
        }

        val durationScore = if (goalMinutes > 0) (asleep.toFloat() / goalMinutes).coerceIn(0f, 1f) else 0f
        val restorativeScore = if (asleep > 0) {
            (((deep + rem).toFloat() / asleep) / IDEAL_RESTORATIVE_FRACTION).coerceIn(0f, 1f)
        } else 0f
        val continuityScore = (1f - (awakenings / 6f)).coerceIn(0f, 1f)
        val efficiencyScore = (efficiency / 100f).coerceIn(0f, 1f)

        val score = (100f * (
            0.40f * durationScore +
                0.25f * restorativeScore +
                0.20f * continuityScore +
                0.15f * efficiencyScore
            )).roundToInt().coerceIn(0, 100)

        return SessionStats(
            totalMinutes = total,
            asleepMinutes = asleep,
            deepMinutes = deep,
            lightMinutes = light,
            remMinutes = rem,
            awakeMinutes = awake,
            awakenings = awakenings,
            sleepOnsetMinutes = onset,
            efficiencyPct = efficiency,
            score = score,
            snoreMinutes = session.snoreMinutes,
            breathRate = breathRate
        )
    }

    /**
     * The night's own quiet and busy levels, which is what every threshold in the app is measured
     * against. Exposed so the live smart alarm scales exactly like the morning report.
     */
    fun quietAndBusy(activity: List<Float>): Pair<Float, Float> {
        if (activity.isEmpty()) return 0f to 1f
        val smooth = movingAverage(activity.toFloatArray(), 5)
        return percentile(smooth, 0.10f) to percentile(smooth, 0.85f)
    }

    /**
     * Reclassifies the still minutes whose breathing was irregular as REM.
     *
     * Only ever converts from deep sleep, never from light: the defining combination is *stillness
     * with unsteady breathing*, and a minute that already looked restless has no claim to be REM.
     *
     * The threshold is a fraction of the night's *own* steadiest breathing rather than a percentile
     * of it. That distinction matters: a percentile would hand back "the least regular 30% of the
     * night" every single time, so a night of flawlessly metronomic breathing would still be
     * reported as one-third REM. Measured against a baseline instead, a night with no irregular
     * stretches correctly yields no REM at all.
     *
     * Bails out when there was never a clean breathing lock, or when too few minutes qualify to be
     * a real REM period rather than noise.
     */
    private fun splitRemFromDeep(stages: IntArray, samples: List<Sample>) {
        val measured = samples.filter { it.breathRate > 0f }.map { it.breathRegularity }
        if (measured.size < MIN_BREATH_MINUTES) return

        val sorted = measured.sorted()
        val steadiest = sorted[(sorted.size * 0.75f).toInt().coerceAtMost(sorted.lastIndex)]
        if (steadiest < MIN_STEADY_REGULARITY) return

        val threshold = steadiest * REM_REGULARITY_RATIO
        if (measured.count { it < threshold } < MIN_REM_MINUTES) return

        for (i in stages.indices) {
            if (stages[i] != Stage.DEEP.ordinal) continue
            // The first REM period of a night does not arrive for well over an hour.
            if (i < REM_EARLIEST_MINUTE) continue
            val sample = samples[i]
            if (sample.breathRate <= 0f) continue
            if (sample.breathRegularity < threshold) stages[i] = Stage.REM.ordinal
        }

        enforceMinimumRuns(stages)
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
     * Removes physiologically impossible flicker: a single minute of deep sleep between two awake
     * minutes is a measurement artefact, not a sleep stage. Real REM periods run ten minutes and up,
     * so they get the longest minimum of all.
     */
    private fun enforceMinimumRuns(stages: IntArray) {
        var i = 0
        while (i < stages.size) {
            var j = i
            while (j < stages.size && stages[j] == stages[i]) j++
            val length = j - i
            val minimum = when (stages[i]) {
                Stage.REM.ordinal -> 5
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

    /** Deep plus REM together, both being the stages that actually restore you. */
    private const val IDEAL_RESTORATIVE_FRACTION = 0.40f
    private const val AWAKENING_MINUTES = 3
    private const val SETTLE_MINUTES = 5
    private const val MIN_MINUTES = 20

    /** Below this much measured breathing, no REM claim is made at all. */
    private const val MIN_BREATH_MINUTES = 40

    /** If even the steadiest quarter of the night was this ragged, the signal was never good enough. */
    private const val MIN_STEADY_REGULARITY = 0.45f

    /** How far below the night's steadiest breathing counts as irregular. */
    private const val REM_REGULARITY_RATIO = 0.75f

    /** Fewer qualifying minutes than this is noise, not a REM period. */
    private const val MIN_REM_MINUTES = 10
    private const val REM_EARLIEST_MINUTE = 60
}
