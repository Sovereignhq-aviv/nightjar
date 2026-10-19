package org.sovereignhq.nightjar.alarm

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

/** One body point from the pose model, in pixels of a square frame, with the model's confidence. */
data class Keypoint(val x: Float, val y: Float, val score: Float)

/**
 * The moves the dance check asks for.
 *
 * Chosen to be impossible to do lying down and hard to confuse with each other, and to need only the
 * upper body and hips in view - a phone propped on a chest of drawers sees that much, where knees and
 * feet would mean propping it across the room. Left and right never matter: a phone's front camera
 * and a half-awake brain disagree about which side is which, so "one hand up" means either hand.
 */
enum class DanceMove(val label: String, val instruction: String) {
    HANDS_UP("Both hands up", "Reach both hands high above your head."),
    ARMS_OUT("Arms out wide", "Stretch both arms straight out to the sides, like a T."),
    ONE_HAND_UP("One hand up", "One hand high above your head, the other down by your side."),
    HANDS_ON_HIPS("Hands on hips", "Both hands on your hips, elbows out.")
}

/**
 * Judging a pose from the 17 points MoveNet finds. Every distance is measured in shoulder widths, so
 * standing near the phone or far from it makes no difference.
 */
object DancePose {

    const val MOVES = 3

    /** Below this, the model is guessing where the point is, and a guess must not pass a move. */
    private const val MIN_SCORE = 0.3f

    // MoveNet's keypoint order.
    private const val L_SHOULDER = 5
    private const val R_SHOULDER = 6
    private const val L_ELBOW = 7
    private const val R_ELBOW = 8
    private const val L_WRIST = 9
    private const val R_WRIST = 10
    private const val L_HIP = 11
    private const val R_HIP = 12

    /** A routine of distinct moves in a random order, so it cannot be done from muscle memory. */
    fun routine(count: Int = MOVES, random: Random = Random.Default): List<DanceMove> =
        DanceMove.entries.shuffled(random).take(count.coerceIn(1, DanceMove.entries.size))

    /** Shoulders and both wrists found: the minimum for judging any move. */
    fun inView(pose: List<Keypoint>): Boolean =
        pose.size >= 17 && listOf(L_SHOULDER, R_SHOULDER, L_WRIST, R_WRIST).all { pose[it].score >= MIN_SCORE }

    fun matches(move: DanceMove, pose: List<Keypoint>): Boolean {
        if (!inView(pose)) return false
        val ls = pose[L_SHOULDER]
        val rs = pose[R_SHOULDER]
        val lw = pose[L_WRIST]
        val rw = pose[R_WRIST]
        val sw = hypot(ls.x - rs.x, ls.y - rs.y)
        if (sw < 1f) return false

        // Image y grows downwards, so "above" is a smaller y.
        fun up(w: Keypoint, s: Keypoint) = w.y < s.y - 0.9f * sw
        fun down(w: Keypoint, s: Keypoint) = w.y > s.y + 0.3f * sw
        fun out(w: Keypoint, s: Keypoint) = abs(w.y - s.y) < 0.5f * sw && abs(w.x - s.x) > 1.1f * sw

        return when (move) {
            DanceMove.HANDS_UP -> up(lw, ls) && up(rw, rs)
            DanceMove.ONE_HAND_UP ->
                (up(lw, ls) && down(rw, rs)) || (up(rw, rs) && down(lw, ls))
            DanceMove.ARMS_OUT ->
                out(lw, ls) && out(rw, rs) && abs(lw.x - rw.x) > 2.8f * sw
            DanceMove.HANDS_ON_HIPS -> {
                val lh = pose[L_HIP]
                val rh = pose[R_HIP]
                val le = pose[L_ELBOW]
                val re = pose[R_ELBOW]
                if (listOf(lh, rh, le, re).any { it.score < MIN_SCORE }) return false
                val mid = (ls.x + rs.x) / 2f
                fun onHip(w: Keypoint, h: Keypoint) = hypot(w.x - h.x, w.y - h.y) < 0.6f * sw
                // Elbows further from the middle than the hands are: the bent-arm shape that tells
                // hands on hips apart from arms simply hanging down.
                fun elbowOut(e: Keypoint, w: Keypoint) = abs(e.x - mid) > abs(w.x - mid) + 0.1f * sw
                onHip(lw, lh) && onHip(rw, rh) && elbowOut(le, lw) && elbowOut(re, rw)
            }
        }
    }
}
