package org.sovereignhq.nightjar.audio

import org.sovereignhq.nightjar.data.EventKind
import kotlin.math.abs

/**
 * Decides what each night noise actually was.
 *
 * Everything starts as a "burst": a stretch of sound above the room's own background level. When
 * the burst ends its shape is measured and matched against four profiles. The features that do the
 * real work:
 *
 *  - **Where the energy sits.** Below 500 Hz means a body noise through a mattress; the 500-4000 Hz
 *    bands mean a voice.
 *  - **How long it lasted.** A fart is under a second. A snore is one to three. A sentence is longer.
 *  - **How fast it arrived.** A snore swells as the breath goes in. A rumble or a thump is loud
 *    immediately and then decays. This is what separates a snore from a fart, which otherwise
 *    occupy nearly the same frequency band.
 *  - **Whether it repeats on a breathing rhythm.** Three low bursts spaced two to six seconds apart
 *    is snoring; one on its own is not, however snore-shaped it looked.
 */
class EventDetector(
    /** dB above the room floor before a sound is worth classifying. From the sensitivity setting. */
    private val triggerDb: Float,
    private val listener: Listener
) {

    /** A `fun interface` so tests and callers can pass a lambda instead of an object. */
    fun interface Listener {
        fun onEvent(event: Event)
    }

    data class Event(
        val kind: EventKind,
        val startMs: Long,
        val durationMs: Long,
        /** Peak loudness of the burst, in dB above the room's background level. */
        val peakAboveFloorDb: Float,
        /** True when this burst was part of a confirmed breathing rhythm. */
        val cadenced: Boolean
    )

    // ---- current burst ----
    private var inBurst = false
    private var burstStartMs = 0L
    private var peakDb = 0f
    private var framesInBurst = 0
    private var framesToPeak = 0
    private var lowRatioSum = 0f
    private var voiceRatioSum = 0f
    private var framesBelow = 0

    // ---- breathing rhythm across bursts ----
    private val lowBurstOnsets = ArrayDeque<Long>()
    private var cadenceActive = false
    private var lastLowBurstMs = 0L

    fun onFrame(f: NightRecorder.Frame) {
        val above = f.aboveFloorDb

        if (above > triggerDb) {
            if (!inBurst) {
                inBurst = true
                burstStartMs = f.timeMs
                peakDb = above
                framesInBurst = 0
                framesToPeak = 0
                lowRatioSum = 0f
                voiceRatioSum = 0f
            }
            framesInBurst++
            if (above > peakDb) {
                peakDb = above
                framesToPeak = framesInBurst
            }
            lowRatioSum += f.lowRatio
            voiceRatioSum += f.voiceRatio
            framesBelow = 0
        } else if (inBurst) {
            // Hysteresis: a burst only ends after a real gap, so one quiet frame mid-snore does
            // not split it into two events.
            if (above > triggerDb - RELEASE_DB) {
                framesInBurst++
                lowRatioSum += f.lowRatio
                voiceRatioSum += f.voiceRatio
                return
            }
            framesBelow++
            if (framesBelow >= GAP_FRAMES) {
                closeBurst(f.timeMs)
            }
        }

        if (cadenceActive && f.timeMs - lastLowBurstMs > CADENCE_EXPIRY_MS) {
            cadenceActive = false
            lowBurstOnsets.clear()
        }
    }

    private fun closeBurst(endMs: Long) {
        val duration = endMs - burstStartMs - GAP_FRAMES * FRAME_MS
        val frames = framesInBurst.coerceAtLeast(1)
        val avgLowRatio = lowRatioSum / frames
        val avgVoiceRatio = voiceRatioSum / frames
        val onsetFraction = framesToPeak.toFloat() / frames

        inBurst = false
        framesBelow = 0

        if (duration < MIN_BURST_MS) return

        val isLowFrequency = avgLowRatio > LOW_RATIO_MIN
        if (isLowFrequency && duration in SNORE_MIN_MS..SNORE_MAX_MS) {
            registerLowBurst(burstStartMs)
        }

        val kind = classify(duration, avgLowRatio, avgVoiceRatio, onsetFraction)
        listener.onEvent(
            Event(
                kind = kind,
                startMs = burstStartMs,
                durationMs = duration,
                peakAboveFloorDb = peakDb,
                cadenced = cadenceActive
            )
        )
    }

    private fun classify(
        durationMs: Long,
        avgLowRatio: Float,
        avgVoiceRatio: Float,
        onsetFraction: Float
    ): EventKind {
        // A hit: over almost instantly, energy spread across the spectrum rather than concentrated
        // low. Rolling over, an elbow into the headboard, something falling.
        if (durationMs < THUMP_MAX_MS && avgLowRatio < LOW_RATIO_MIN && onsetFraction < SHARP_ONSET) {
            return EventKind.THUMP
        }

        if (avgVoiceRatio > VOICE_RATIO_MIN && durationMs >= VOICE_MIN_MS) {
            return EventKind.VOICE
        }

        if (avgLowRatio > LOW_RATIO_MIN) {
            // Rhythm settles it: in a confirmed breathing pattern, a low burst is a snore.
            if (cadenceActive && durationMs in SNORE_MIN_MS..SNORE_MAX_MS) return EventKind.SNORE

            // Short and sudden, no rhythm behind it. Gut noise.
            if (durationMs <= RUMBLE_MAX_MS) return EventKind.RUMBLE

            // Long, low and swelling looks like a snore even without a confirmed rhythm yet.
            if (durationMs in SNORE_MIN_MS..SNORE_MAX_MS && onsetFraction >= SHARP_ONSET) {
                return EventKind.SNORE
            }
        }

        return EventKind.OTHER
    }

    private fun registerLowBurst(onsetMs: Long) {
        lastLowBurstMs = onsetMs
        lowBurstOnsets.addLast(onsetMs)
        while (lowBurstOnsets.size > 4) lowBurstOnsets.removeFirst()
        if (lowBurstOnsets.size < 3) return

        val gaps = lowBurstOnsets.toList().zipWithNext { a, b -> b - a }
        val spacedLikeBreathing = gaps.all { it in MIN_CADENCE_MS..MAX_CADENCE_MS }
        // Breathing is steady; a run of random thumps is not.
        val steady = gaps.size < 2 || gaps.zipWithNext { a, b ->
            abs(a - b).toFloat() / maxOf(a, b).toFloat()
        }.all { it < MAX_JITTER }

        if (spacedLikeBreathing && steady) cadenceActive = true
    }

    private companion object {
        const val FRAME_MS = 32L

        /** How far below the trigger a frame must fall before it counts towards ending a burst. */
        const val RELEASE_DB = 4f

        /** ~190 ms of quiet closes a burst. */
        const val GAP_FRAMES = 6
        const val MIN_BURST_MS = 120L

        const val LOW_RATIO_MIN = 1.6f
        const val VOICE_RATIO_MIN = 1.4f
        const val VOICE_MIN_MS = 300L

        const val THUMP_MAX_MS = 400L
        const val RUMBLE_MAX_MS = 1_400L
        const val SNORE_MIN_MS = 400L
        const val SNORE_MAX_MS = 3_500L

        /** Peak inside the first fifth of the burst means it hit rather than swelled. */
        const val SHARP_ONSET = 0.2f

        const val MIN_CADENCE_MS = 1_500L
        const val MAX_CADENCE_MS = 7_000L
        const val MAX_JITTER = 0.45f
        const val CADENCE_EXPIRY_MS = 30_000L
    }
}
