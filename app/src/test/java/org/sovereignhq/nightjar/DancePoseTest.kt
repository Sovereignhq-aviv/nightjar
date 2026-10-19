package org.sovereignhq.nightjar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sovereignhq.nightjar.alarm.DanceMove
import org.sovereignhq.nightjar.alarm.DancePose
import org.sovereignhq.nightjar.alarm.Exercise
import org.sovereignhq.nightjar.alarm.Keypoint
import org.sovereignhq.nightjar.alarm.RepCounter
import kotlin.random.Random

/**
 * Poses built by hand on a 100-pixel shoulder width, standing facing the camera: the person's left
 * shoulder at x=250, right at x=150 (the camera sees them mirrored), shoulders at y=200.
 */
class DancePoseTest {

    private fun pose(
        lw: Pair<Float, Float>,
        rw: Pair<Float, Float>,
        le: Pair<Float, Float> = 270f to 280f,
        re: Pair<Float, Float> = 130f to 280f,
        wristScore: Float = 0.9f
    ): List<Keypoint> {
        val points = MutableList(17) { Keypoint(0f, 0f, 0.9f) }
        points[0] = Keypoint(200f, 120f, 0.9f) // nose
        points[5] = Keypoint(250f, 200f, 0.9f) // left shoulder
        points[6] = Keypoint(150f, 200f, 0.9f) // right shoulder
        points[7] = Keypoint(le.first, le.second, 0.9f)
        points[8] = Keypoint(re.first, re.second, 0.9f)
        points[9] = Keypoint(lw.first, lw.second, wristScore)
        points[10] = Keypoint(rw.first, rw.second, wristScore)
        points[11] = Keypoint(230f, 380f, 0.9f) // left hip
        points[12] = Keypoint(170f, 380f, 0.9f) // right hip
        return points
    }

    private val handsUp = pose(lw = 260f to 40f, rw = 140f to 40f)
    private val armsOut = pose(lw = 420f to 205f, rw = -20f to 195f)
    private val oneUp = pose(lw = 260f to 40f, rw = 150f to 360f)
    private val onHips = pose(lw = 235f to 375f, rw = 165f to 375f, le = 320f to 300f, re = 80f to 300f)
    private val hangingDown = pose(lw = 255f to 390f, rw = 145f to 390f, le = 255f to 300f, re = 145f to 300f)

    @Test
    fun eachMoveIsRecognised() {
        assertTrue(DancePose.matches(DanceMove.HANDS_UP, handsUp))
        assertTrue(DancePose.matches(DanceMove.ARMS_OUT, armsOut))
        assertTrue(DancePose.matches(DanceMove.ONE_HAND_UP, oneUp))
        assertTrue(DancePose.matches(DanceMove.HANDS_ON_HIPS, onHips))
    }

    @Test
    fun movesAreNotMistakenForEachOther() {
        val poses = mapOf(
            DanceMove.HANDS_UP to handsUp,
            DanceMove.ARMS_OUT to armsOut,
            DanceMove.ONE_HAND_UP to oneUp,
            DanceMove.HANDS_ON_HIPS to onHips
        )
        for ((done, p) in poses) {
            for (asked in DanceMove.entries) {
                if (asked != done) assertFalse("$done passed as $asked", DancePose.matches(asked, p))
            }
        }
    }

    @Test
    fun armsHangingDownPassNothing() {
        DanceMove.entries.forEach { assertFalse(it.name, DancePose.matches(it, hangingDown)) }
    }

    @Test
    fun eitherHandCountsForOneHandUp() {
        val rightUp = pose(lw = 250f to 360f, rw = 140f to 40f)
        assertTrue(DancePose.matches(DanceMove.ONE_HAND_UP, rightUp))
    }

    @Test
    fun uncertainWristsPassNothing() {
        val blurry = pose(lw = 260f to 40f, rw = 140f to 40f, wristScore = 0.1f)
        assertFalse(DancePose.inView(blurry))
        assertFalse(DancePose.matches(DanceMove.HANDS_UP, blurry))
    }

    @Test
    fun aRoutineIsDistinctMoves() {
        val routine = DancePose.routine(3, Random(7))
        assertEquals(3, routine.size)
        assertEquals(3, routine.toSet().size)
    }

    private fun withKnees(base: List<Keypoint>, kneeY: Float): List<Keypoint> =
        base.toMutableList().also {
            it[13] = Keypoint(230f, kneeY, 0.9f)
            it[14] = Keypoint(170f, kneeY, 0.9f)
        }

    @Test
    fun jumpingJacksCountEachUpAndDown() {
        val counter = RepCounter(Exercise.JUMPING_JACKS)
        val sequence = listOf(hangingDown, handsUp, handsUp, hangingDown, armsOut, handsUp, hangingDown)
        sequence.forEach { counter.feed(it) }
        assertEquals(2, counter.reps)
    }

    @Test
    fun goingUpWithoutComingDownIsNotARep() {
        val counter = RepCounter(Exercise.JUMPING_JACKS)
        listOf(hangingDown, handsUp, handsUp, handsUp).forEach { counter.feed(it) }
        assertEquals(0, counter.reps)
    }

    @Test
    fun squatsCountDownAndBackUp() {
        // Hips at y=380; standing knees 130px lower (1.3 shoulder widths), squatting knees level.
        val standing = withKnees(hangingDown, 510f)
        val squatting = withKnees(hangingDown, 400f)
        val counter = RepCounter(Exercise.SQUATS)
        listOf(standing, squatting, standing, squatting, squatting, standing).forEach { counter.feed(it) }
        assertEquals(2, counter.reps)
    }

    @Test
    fun squatsNeedTheKneesInView() {
        val noKnees = hangingDown.toMutableList().also {
            it[13] = Keypoint(230f, 400f, 0.1f)
            it[14] = Keypoint(170f, 400f, 0.1f)
        }
        assertFalse(DancePose.squatting(noKnees))
        assertFalse(DancePose.standing(noKnees))
    }
}
