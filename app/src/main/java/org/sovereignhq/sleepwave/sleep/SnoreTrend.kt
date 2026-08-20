package org.sovereignhq.sleepwave.sleep

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Compares this week's snoring against last week's and says whether it has actually moved.
 *
 * The whole difficulty here is not the arithmetic, it is refusing to cry wolf. A percentage on a
 * small base is nonsense - two minutes becoming six is "up 200%" and means nothing at all - and an
 * app that announces a dramatic change every week teaches you to ignore it. So a change has to clear
 * both a relative and an absolute bar before it gets mentioned, percentages are only used when the
 * baseline is big enough to carry one, and anything short of that is reported as steady rather than
 * dressed up.
 */
object SnoreTrend {

    enum class Direction { UP, DOWN, STEADY }

    data class Verdict(
        val headline: String,
        val detail: String,
        val direction: Direction
    )

    /**
     * [recent] and [previous] are snoring minutes per night for two consecutive stretches, most
     * recent first. Returns null when there is not enough history to say anything honest.
     */
    fun compare(recent: List<Int>, previous: List<Int>): Verdict? {
        if (recent.size < MIN_NIGHTS || previous.size < MIN_NIGHTS) return null

        val recentAvg = recent.average().toFloat()
        val previousAvg = previous.average().toFloat()
        val delta = recentAvg - previousAvg

        val sample = "Averaging ${recentAvg.roundToInt()} min a night across the last " +
            "${recent.size}, against ${previousAvg.roundToInt()} min over the ${previous.size} before."

        // Both bars have to be cleared. The absolute one stops a jump from 3 to 9 minutes being
        // announced as a tripling; the relative one stops 60 to 70 minutes being called a change.
        if (abs(delta) < MIN_ABSOLUTE_MINUTES) {
            return Verdict(
                headline = if (recentAvg < 1f) "No snoring worth reporting" else "Snoring is steady",
                detail = sample,
                direction = Direction.STEADY
            )
        }

        val percentage = if (previousAvg >= MIN_BASE_FOR_PERCENTAGE) {
            (delta / previousAvg * 100f).roundToInt()
        } else {
            null
        }

        if (percentage != null && abs(percentage) < MIN_PERCENT_CHANGE) {
            return Verdict("Snoring is steady", sample, Direction.STEADY)
        }

        val up = delta > 0f
        val headline = when {
            percentage != null && up -> "Snoring up ${percentage}% this week"
            percentage != null -> "Snoring down ${abs(percentage)}% this week"
            up -> "Snoring up by ${delta.roundToInt()} min a night"
            else -> "Snoring down by ${abs(delta).roundToInt()} min a night"
        }

        val caution = if (recent.size < COMFORTABLE_SAMPLE || previous.size < COMFORTABLE_SAMPLE) {
            " Only a few nights either side, so treat it as a hint."
        } else {
            ""
        }

        return Verdict(
            headline = headline,
            detail = sample + caution,
            direction = if (up) Direction.UP else Direction.DOWN
        )
    }

    /** Splits a newest-first history into the two stretches to compare. */
    fun windows(minutesNewestFirst: List<Int>, windowNights: Int = 7): Pair<List<Int>, List<Int>> {
        val recent = minutesNewestFirst.take(windowNights)
        val previous = minutesNewestFirst.drop(windowNights).take(windowNights)
        return recent to previous
    }

    /** Fewer than this on either side and there is nothing worth comparing. */
    private const val MIN_NIGHTS = 3

    /** Enough nights that a week-on-week number is not mostly luck. */
    private const val COMFORTABLE_SAMPLE = 5

    /** A change smaller than this is not something anyone would notice. */
    private const val MIN_ABSOLUTE_MINUTES = 8f

    /** Below this baseline a percentage is arithmetic theatre, so plain minutes are used instead. */
    private const val MIN_BASE_FOR_PERCENTAGE = 10f

    private const val MIN_PERCENT_CHANGE = 20
}
