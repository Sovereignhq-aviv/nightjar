package org.sovereignhq.nightjar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import org.sovereignhq.nightjar.alarm.DancePose
import org.sovereignhq.nightjar.alarm.Exercise
import org.sovereignhq.nightjar.alarm.PoseDetector
import org.sovereignhq.nightjar.alarm.RepCounter

/** Faster than the dance's 330ms: a jumping jack is over in under a second, and both ends must be seen. */
private const val WORKOUT_FRAME_MS = 200L

/**
 * The sporty version of the dance check: every [Exercise] in turn, counted rep by rep in front of the
 * selfie camera. The stick figure moves between the two ends of the rep, so the shape to copy is
 * obvious without reading.
 *
 * Same contract as [DanceRoutine]: [onProgress] after each finished exercise, [onDone] on the main
 * thread at the end.
 */
@Composable
fun WorkoutRoutine(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onProgress: () -> Unit = {}
) {
    val context = LocalContext.current
    val detector = remember { PoseDetector.create(context) }
    DisposableEffect(detector) { onDispose { detector?.close() } }
    val latestDone by rememberUpdatedState(onDone)
    val latestProgress by rememberUpdatedState(onProgress)
    val exercises = remember { Exercise.entries.toList() }

    var index by remember { mutableIntStateOf(0) }
    var reps by remember { mutableIntStateOf(0) }
    var inView by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    val counter = remember(index) { RepCounter(exercises[index.coerceAtMost(exercises.lastIndex)]) }

    var figureAtTop by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(700)
            figureAtTop = !figureAtTop
        }
    }

    if (detector == null) {
        Text(
            "The workout check is not working on this phone right now.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        return
    }

    val exercise = exercises[index.coerceAtMost(exercises.lastIndex)]

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Exercise ${index + 1} of ${exercises.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            ExerciseFigure(
                exercise = exercise,
                atTop = figureAtTop,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(width = 84.dp, height = 104.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    "${exercise.label}: $reps of ${exercise.reps}",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    exercise.instruction,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        PlaceCamera(
            front = true,
            intervalMs = WORKOUT_FRAME_MS,
            onFrame = { frame ->
                if (finished) return@PlaceCamera
                val pose = detector.detect(frame) ?: return@PlaceCamera
                val current = exercises[index.coerceAtMost(exercises.lastIndex)]
                inView = if (current == Exercise.SQUATS) DancePose.legsInView(pose) else DancePose.inView(pose)
                if (!counter.feed(pose)) return@PlaceCamera
                reps = counter.reps
                if (reps < current.reps) return@PlaceCamera
                val main = ContextCompat.getMainExecutor(context)
                if (index + 1 >= exercises.size) {
                    finished = true
                    main.execute { latestDone() }
                } else {
                    reps = 0
                    index += 1
                    main.execute { latestProgress() }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(18.dp))
        )
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { reps / exercise.reps.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = when {
                !inView && exercise == Exercise.SQUATS ->
                    "Prop the phone up and step back until your knees are in view."
                !inView -> "Prop the phone up and step back until both hands are in view."
                else -> "Keep going"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

/** The two ends of the rep, drawn on the same 100 x 120 grid as the dance figure. */
@Composable
private fun ExerciseFigure(exercise: Exercise, atTop: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val sx = size.width / 100f
        val sy = size.height / 120f
        fun p(x: Float, y: Float) = Offset(x * sx, y * sy)
        val width = 5.dp.toPx()
        fun limb(vararg points: Pair<Float, Float>) =
            points.map { p(it.first, it.second) }.zipWithNext { a, b ->
                drawLine(color, a, b, strokeWidth = width, cap = StrokeCap.Round)
            }

        // A squat lowers the whole upper body; everything above the hips shifts down by `drop`.
        val drop = if (exercise == Exercise.SQUATS && atTop) 26f else 0f
        drawCircle(color, radius = 9f * sx, center = p(50f, 16f + drop), style = Stroke(width))
        limb(50f to 26f + drop, 50f to 72f + drop)
        limb(40f to 34f + drop, 60f to 34f + drop)

        when (exercise) {
            Exercise.JUMPING_JACKS -> if (atTop) {
                limb(40f to 34f, 30f to 16f, 26f to 0f)
                limb(60f to 34f, 70f to 16f, 74f to 0f)
                limb(50f to 72f, 26f to 116f)
                limb(50f to 72f, 74f to 116f)
            } else {
                limb(40f to 34f, 38f to 52f, 38f to 70f)
                limb(60f to 34f, 62f to 52f, 62f to 70f)
                limb(50f to 72f, 44f to 116f)
                limb(50f to 72f, 56f to 116f)
            }
            Exercise.SQUATS -> if (atTop) {
                // Arms straight out in front for balance, knees bent out to the sides.
                limb(40f to 34f + drop, 30f to 44f + drop, 22f to 52f + drop)
                limb(60f to 34f + drop, 70f to 44f + drop, 78f to 52f + drop)
                limb(50f to 98f, 30f to 104f, 36f to 116f)
                limb(50f to 98f, 70f to 104f, 64f to 116f)
            } else {
                limb(40f to 34f, 30f to 44f, 22f to 52f)
                limb(60f to 34f, 70f to 44f, 78f to 52f)
                limb(50f to 72f, 42f to 116f)
                limb(50f to 72f, 58f to 116f)
            }
        }
    }
}
