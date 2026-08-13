package org.sovereignhq.sleepwave.sleep

import org.sovereignhq.sleepwave.audio.NightRecorder

/**
 * Collapses ~1,875 audio frames per minute into one restlessness number between 0 and 1.
 *
 * Two things matter and they are not the same: how *often* something happened (a restless
 * sleeper generates many small sounds) and how *loud* the worst of it was (one bang while
 * otherwise still). Both are counted, frequency weighted higher, because a steady trickle of
 * movement separates light sleep from deep sleep better than isolated peaks do.
 */
class ActivityAggregator {

    private var events = 0
    private var frames = 0
    private var peakAboveFloor = 0f
    private var snoreFramesThisMinute = 0

    fun onFrame(f: NightRecorder.Frame) {
        frames++
        val above = f.aboveFloorDb
        if (above > EVENT_DB) events++
        if (above > peakAboveFloor) peakAboveFloor = above
    }

    fun markSnoring() {
        snoreFramesThisMinute++
    }

    /**
     * Finishes the minute and returns (activity, peakDb, snoring), resetting for the next one.
     * [motionCount] and [motionPeak] come from the accelerometer; pass zeros when motion
     * sensing is off.
     */
    fun closeMinute(motionCount: Int, motionPeak: Float): MinuteResult {
        val eventRate = if (frames > 0) events.toFloat() / frames else 0f

        // Roughly 5% of frames carrying sound already means a restless minute.
        val fromRate = (eventRate / 0.05f).coerceIn(0f, 1f)
        val fromPeak = (peakAboveFloor / 24f).coerceIn(0f, 1f)
        val audioActivity = 0.65f * fromRate + 0.35f * fromPeak

        val fromMotionCount = (motionCount / 18f).coerceIn(0f, 1f)
        val fromMotionPeak = (motionPeak / 2.5f).coerceIn(0f, 1f)
        val motionActivity = maxOf(fromMotionCount, 0.7f * fromMotionPeak)

        // Whichever sensor noticed more is trusted: a still-but-noisy minute and a
        // quiet-but-thrashing minute are both restless.
        val activity = maxOf(audioActivity, motionActivity).coerceIn(0f, 1f)

        val result = MinuteResult(
            activity = activity,
            peakDb = peakAboveFloor,
            snoring = snoreFramesThisMinute > 0
        )

        events = 0
        frames = 0
        peakAboveFloor = 0f
        snoreFramesThisMinute = 0
        return result
    }

    data class MinuteResult(val activity: Float, val peakDb: Float, val snoring: Boolean)

    private companion object {
        /** dB above the room floor before a frame counts as "something happened". */
        const val EVENT_DB = 6f
    }
}
