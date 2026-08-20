package org.sovereignhq.nightjar.sleep

/**
 * The decision that makes this app worth using: inside the wake window, ring at a moment when
 * you are already stirring rather than dragging you out of deep sleep.
 *
 * Two independent triggers, either one is enough:
 *
 *  - **Level.** Restlessness over the last few minutes is high relative to the rest of the
 *    night, so you are in light sleep or already half-awake.
 *  - **Rising edge.** A sharp jump above the preceding stretch - the moment you turn over.
 *
 * The level threshold falls steadily as the window runs out. Early in the window only clear
 * light sleep qualifies; by the end almost anything does. That way the alarm nearly always
 * finds a good moment instead of slamming into the hard deadline.
 */
object SmartAlarm {

    data class Decision(val wake: Boolean, val smart: Boolean, val reason: String)

    /**
     * @param minutesIntoWindow how long the wake window has been open
     * @param windowMinutes total length of the window
     * @param recent per-minute activity, oldest first, ideally the last ~10 minutes
     * @param nightQuiet the night's own quiet level (10th percentile)
     * @param nightBusy the night's own busy level (85th percentile)
     */
    fun evaluate(
        minutesIntoWindow: Int,
        windowMinutes: Int,
        recent: List<Float>,
        nightQuiet: Float,
        nightBusy: Float
    ): Decision {
        if (minutesIntoWindow >= windowMinutes) {
            return Decision(wake = true, smart = false, reason = "Window ended")
        }
        // Give it a minute so an alarm never fires on the instant the window opens.
        if (minutesIntoWindow < 1 || recent.isEmpty()) {
            return Decision(wake = false, smart = false, reason = "Too early in window")
        }

        val span = (nightBusy - nightQuiet).takeIf { it > 0.04f } ?: 1f
        fun normalise(v: Float) = ((v - nightQuiet) / span).coerceIn(0f, 1f)

        val progress = minutesIntoWindow.toFloat() / windowMinutes
        val threshold = START_THRESHOLD - (START_THRESHOLD - END_THRESHOLD) * progress

        val lookback = recent.takeLast(3)
        val currentLevel = normalise(lookback.average().toFloat())
        if (currentLevel >= threshold) {
            return Decision(wake = true, smart = true, reason = "Light sleep detected")
        }

        if (recent.size >= 6) {
            val latest = normalise(recent.last())
            val baseline = normalise(recent.dropLast(1).takeLast(5).average().toFloat())
            if (latest > baseline + RISE_DELTA && latest > MIN_RISE_LEVEL) {
                return Decision(wake = true, smart = true, reason = "You started stirring")
            }
        }

        return Decision(wake = false, smart = false, reason = "Still asleep")
    }

    /** Early in the window, only convincing light sleep counts. */
    private const val START_THRESHOLD = 0.55f
    /** By the end of the window, almost any movement counts. */
    private const val END_THRESHOLD = 0.20f
    private const val RISE_DELTA = 0.22f
    private const val MIN_RISE_LEVEL = 0.30f
}
