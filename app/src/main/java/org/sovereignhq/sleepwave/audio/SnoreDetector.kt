package org.sovereignhq.sleepwave.audio

import kotlin.math.abs

/**
 * Tells snoring apart from other night noise using two properties a snore has and a cough,
 * a car, or a partner rolling over does not:
 *
 *  1. Energy sits in the low band (roughly 60-500 Hz) rather than spread across the spectrum.
 *  2. It repeats on a breathing cadence - a burst every 2 to 6 seconds, fairly regularly.
 *
 * Requiring the cadence is what keeps the false-positive rate low. A single low rumble is
 * ignored; three in rhythm is a snore.
 */
class SnoreDetector(private val listener: Listener) {

    interface Listener {
        /** A single low-frequency burst that looked snore-shaped. */
        fun onSnoreBurst(timeMs: Long, peakDb: Float)
        /** Three or more bursts in a breathing rhythm - a real snoring stretch. */
        fun onSnoreEpisode(timeMs: Long, peakDb: Float)
        /** Loud, broadband, not snore-shaped: talking, a door, a dog. */
        fun onNoiseEvent(timeMs: Long, peakDb: Float)
    }

    private var inBurst = false
    private var burstStartMs = 0L
    private var burstPeakDb = -90f
    private var framesBelow = 0

    private val recentOnsets = ArrayDeque<Long>()
    private var episodeActive = false
    private var lastBurstEndMs = 0L

    private var loudSinceMs = 0L
    private var loudPeakDb = -90f
    private var loudIsBroadband = false

    fun onFrame(f: NightRecorder.Frame) {
        val above = f.aboveFloorDb
        val lowRatio = f.lowEnergy / (f.midEnergy + f.highEnergy + 1e-9f)

        // ---- snore-shaped burst tracking ----
        val snoreLike = above > BURST_DB && lowRatio > LOW_RATIO_MIN
        if (snoreLike) {
            if (!inBurst) {
                inBurst = true
                burstStartMs = f.timeMs
                burstPeakDb = above
            } else if (above > burstPeakDb) {
                burstPeakDb = above
            }
            framesBelow = 0
        } else if (inBurst) {
            framesBelow++
            if (framesBelow >= GAP_FRAMES) {
                val duration = f.timeMs - burstStartMs
                inBurst = false
                framesBelow = 0
                if (duration in MIN_BURST_MS..MAX_BURST_MS) {
                    lastBurstEndMs = f.timeMs
                    listener.onSnoreBurst(burstStartMs, burstPeakDb)
                    registerOnset(burstStartMs, burstPeakDb)
                }
            }
        }

        // A long quiet stretch means the snoring run is over.
        if (episodeActive && f.timeMs - lastBurstEndMs > EPISODE_END_MS) {
            episodeActive = false
            recentOnsets.clear()
        }

        // ---- other night noise ----
        val broadband = lowRatio < BROADBAND_RATIO_MAX
        if (above > NOISE_DB) {
            if (loudSinceMs == 0L) {
                loudSinceMs = f.timeMs
                loudPeakDb = above
                loudIsBroadband = broadband
            } else {
                if (above > loudPeakDb) loudPeakDb = above
                if (broadband) loudIsBroadband = true
            }
        } else if (loudSinceMs != 0L) {
            val duration = f.timeMs - loudSinceMs
            val peak = loudPeakDb
            val wasBroadband = loudIsBroadband
            loudSinceMs = 0L
            loudPeakDb = -90f
            loudIsBroadband = false
            if (duration >= MIN_NOISE_MS && wasBroadband) {
                listener.onNoiseEvent(f.timeMs - duration, peak)
            }
        }
    }

    private fun registerOnset(onsetMs: Long, peakDb: Float) {
        recentOnsets.addLast(onsetMs)
        while (recentOnsets.size > 4) recentOnsets.removeFirst()
        if (recentOnsets.size < 3) return

        val onsets = recentOnsets.toList()
        val gaps = onsets.zipWithNext { a, b -> b - a }
        val inCadence = gaps.all { it in MIN_CADENCE_MS..MAX_CADENCE_MS }
        // Regularity check: breathing is steady, random thumps are not.
        val jitterOk = gaps.size < 2 || gaps.zipWithNext { a, b ->
            abs(a - b).toFloat() / maxOf(a, b).toFloat()
        }.all { it < MAX_JITTER }

        if (inCadence && jitterOk && !episodeActive) {
            episodeActive = true
            listener.onSnoreEpisode(onsets.first(), peakDb)
        }
    }

    private companion object {
        /** dB above the room floor before a frame counts as a burst. */
        const val BURST_DB = 8f
        /** Low-band energy must beat everything above it by this much. */
        const val LOW_RATIO_MIN = 1.8f
        /** ~200 ms of quiet closes a burst. */
        const val GAP_FRAMES = 6
        const val MIN_BURST_MS = 400L
        const val MAX_BURST_MS = 3_500L
        /** Inhale-to-inhale spacing for sleeping breathing. */
        const val MIN_CADENCE_MS = 1_500L
        const val MAX_CADENCE_MS = 7_000L
        const val MAX_JITTER = 0.45f
        const val EPISODE_END_MS = 30_000L

        const val NOISE_DB = 20f
        const val MIN_NOISE_MS = 600L
        const val BROADBAND_RATIO_MAX = 1.2f
    }
}
