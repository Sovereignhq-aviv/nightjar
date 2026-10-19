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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.sovereignhq.nightjar.alarm.DanceMove
import org.sovereignhq.nightjar.alarm.DancePose
import org.sovereignhq.nightjar.alarm.PoseDetector

/** Each move held for about a second (three frames at 330ms), so a passing flail does not count. */
private const val HOLD_FRAMES = 3
private const val FRAME_MS = 330L

/**
 * The dance check: a few moves shown as a stick figure, copied in front of the selfie camera.
 *
 * A stick figure rather than words alone, because at 7am a picture of the pose is quicker to copy
 * than a sentence describing it. The figure faces you like a mirror would.
 *
 * [onProgress] fires each time a move is done, so the alarm can stretch its silence while you are
 * visibly busy. [onDone] runs on the main thread. When the model cannot load, it says so in place of
 * the camera, and the caller's own fallback button is the way out.
 */
@Composable
fun DanceRoutine(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onProgress: () -> Unit = {}
) {
    val context = LocalContext.current
    val detector = remember { PoseDetector.create(context) }
    DisposableEffect(detector) { onDispose { detector?.close() } }
    val routine = remember { DancePose.routine() }
    val latestDone by rememberUpdatedState(onDone)
    val latestProgress by rememberUpdatedState(onProgress)

    var index by remember { mutableIntStateOf(0) }
    var held by remember { mutableIntStateOf(0) }
    var inView by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    if (detector == null) {
        Text(
            "The dance check is not working on this phone right now.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        return
    }

    val move = routine[index.coerceAtMost(routine.lastIndex)]

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Move ${index + 1} of ${routine.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            StickFigure(
                move = move,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(width = 84.dp, height = 104.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(move.label, style = MaterialTheme.typography.headlineSmall)
                Text(
                    move.instruction,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        PlaceCamera(
            front = true,
            intervalMs = FRAME_MS,
            onFrame = { frame ->
                if (finished) return@PlaceCamera
                val pose = detector.detect(frame) ?: return@PlaceCamera
                inView = DancePose.inView(pose)
                val current = routine[index.coerceAtMost(routine.lastIndex)]
                held = if (DancePose.matches(current, pose)) held + 1 else 0
                if (held >= HOLD_FRAMES) {
                    held = 0
                    val main = ContextCompat.getMainExecutor(context)
                    if (index + 1 >= routine.size) {
                        finished = true
                        main.execute { latestDone() }
                    } else {
                        index += 1
                        main.execute { latestProgress() }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(18.dp))
        )
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { held / HOLD_FRAMES.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = when {
                !inView -> "Prop the phone up and step back until both hands are in view."
                held > 0 -> "That's it - hold it"
                else -> "Copy the figure"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

/**
 * The move, drawn. Coordinates are on a 100 x 120 grid, scaled to the canvas: head at the top,
 * shoulders at y=34, hips at y=72.
 */
@Composable
private fun StickFigure(move: DanceMove, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val sx = size.width / 100f
        val sy = size.height / 120f
        fun p(x: Float, y: Float) = Offset(x * sx, y * sy)
        val width = 5.dp.toPx()
        fun limb(vararg points: Pair<Float, Float>) = drawLimb(points.map { p(it.first, it.second) }, color, width)

        drawCircle(color, radius = 9f * sx, center = p(50f, 16f), style = Stroke(width))
        limb(50f to 26f, 50f to 72f)
        limb(50f to 72f, 38f to 116f)
        limb(50f to 72f, 62f to 116f)

        when (move) {
            DanceMove.HANDS_UP -> {
                limb(40f to 34f, 36f to 18f, 38f to 2f)
                limb(60f to 34f, 64f to 18f, 62f to 2f)
            }
            DanceMove.ARMS_OUT -> {
                limb(40f to 34f, 22f to 34f, 4f to 34f)
                limb(60f to 34f, 78f to 34f, 96f to 34f)
            }
            DanceMove.ONE_HAND_UP -> {
                limb(40f to 34f, 36f to 18f, 38f to 2f)
                limb(60f to 34f, 64f to 52f, 66f to 70f)
            }
            DanceMove.HANDS_ON_HIPS -> {
                limb(40f to 34f, 24f to 52f, 42f to 70f)
                limb(60f to 34f, 76f to 52f, 58f to 70f)
            }
        }
        limb(40f to 34f, 60f to 34f)
    }
}

private fun DrawScope.drawLimb(points: List<Offset>, color: Color, width: Float) {
    points.zipWithNext { a, b -> drawLine(color, a, b, strokeWidth = width, cap = StrokeCap.Round) }
}
