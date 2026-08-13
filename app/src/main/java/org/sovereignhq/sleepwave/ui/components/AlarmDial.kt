package org.sovereignhq.sleepwave.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sovereignhq.sleepwave.ui.formatHourMinute
import org.sovereignhq.sleepwave.ui.theme.DataColors
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The alarm time as a dial you drag, rather than a system time picker.
 *
 * Two reasons this beats the stock dialog. Setting an alarm is the last thing you do before sleeping,
 * often one-handed in the dark, and dragging a ring needs no aim and no confirm button — there is no
 * OK to press, the value is simply set. And the smart window has somewhere to live: it is drawn as
 * the lighter band running back from the handle, so "wakes you up to 30 minutes early" stops being a
 * sentence and becomes the shape of the thing.
 *
 * A twelve-hour dial, because it gives twice the angular precision of a 24-hour one, with an AM/PM
 * pair below to say which half. Snapped to five minutes: nobody needs a 06:53 alarm.
 */
@Composable
fun AlarmDial(
    hour: Int,
    minute: Int,
    windowMinutes: Int,
    enabled: Boolean,
    onChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
    diameter: Int = 268
) {
    val ring = MaterialTheme.colorScheme.surfaceVariant
    val tickColor = MaterialTheme.colorScheme.outline
    val accent = MaterialTheme.colorScheme.primary
    val windowColor = DataColors.stageRem
    val handleInk = MaterialTheme.colorScheme.onPrimary
    val isPm = hour >= 12

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(diameter.dp), contentAlignment = Alignment.Center) {

            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(enabled, isPm) {
                        if (!enabled) return@pointerInput
                        detectTapGestures { offset ->
                            emit(offset, size, isPm, onChange)
                        }
                    }
                    .pointerInput(enabled, isPm) {
                        if (!enabled) return@pointerInput
                        detectDragGestures { change, _ ->
                            emit(change.position, size, isPm, onChange)
                        }
                    }
            ) {
                val stroke = 22f * density
                val radius = min(size.width, size.height) / 2f - stroke / 2f - 2f * density
                val centre = Offset(size.width / 2f, size.height / 2f)

                // The track.
                drawCircle(
                    color = ring,
                    radius = radius,
                    center = centre,
                    style = Stroke(width = stroke)
                )

                // Hour ticks, longer at the quarters.
                for (tick in 0 until 12) {
                    val deg = tick * 30f
                    val rad = Math.toRadians(deg.toDouble())
                    val long = tick % 3 == 0
                    val inner = radius - (if (long) 9f else 5f) * density
                    val outer = radius + (if (long) 9f else 5f) * density
                    drawLine(
                        color = tickColor,
                        start = Offset(
                            centre.x + (inner * sin(rad)).toFloat(),
                            centre.y - (inner * cos(rad)).toFloat()
                        ),
                        end = Offset(
                            centre.x + (outer * sin(rad)).toFloat(),
                            centre.y - (outer * cos(rad)).toFloat()
                        ),
                        strokeWidth = if (long) 2.5f * density else 1.5f * density,
                        cap = StrokeCap.Round
                    )
                }

                val handleDeg = ((hour % 12) * 60 + minute) / 720f * 360f

                // The smart window, running back from the wake-up time. This is when you might
                // actually be woken, which is more honest than a single point on the dial.
                if (windowMinutes > 0 && enabled) {
                    val windowDeg = windowMinutes / 720f * 360f
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(
                                windowColor.copy(alpha = 0.15f),
                                windowColor.copy(alpha = 0.75f)
                            )
                        ),
                        startAngle = handleDeg - windowDeg - 90f,
                        sweepAngle = windowDeg,
                        useCenter = false,
                        topLeft = Offset(centre.x - radius, centre.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }

                // A short lead-in of accent right at the handle, so the wake time itself reads as
                // the destination rather than just the end of the window.
                if (enabled) {
                    drawArc(
                        color = accent,
                        startAngle = handleDeg - 2.5f - 90f,
                        sweepAngle = 5f,
                        useCenter = false,
                        topLeft = Offset(centre.x - radius, centre.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }

                // The handle.
                val handleRad = Math.toRadians(handleDeg.toDouble())
                val handleCentre = Offset(
                    centre.x + (radius * sin(handleRad)).toFloat(),
                    centre.y - (radius * cos(handleRad)).toFloat()
                )
                drawCircle(
                    color = if (enabled) accent else tickColor,
                    radius = 15f * density,
                    center = handleCentre
                )
                drawCircle(
                    color = handleInk,
                    radius = 5f * density,
                    center = handleCentre
                )
            }

            // Anchor labels, placed as text rather than drawn into the canvas so they inherit the
            // type scale and stay legible at any font-size setting.
            DialLabel("12", Modifier.align(Alignment.TopCenter).padding(top = 34.dp))
            DialLabel("3", Modifier.align(Alignment.CenterEnd).padding(end = 34.dp))
            DialLabel("6", Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp))
            DialLabel("9", Modifier.align(Alignment.CenterStart).padding(start = 34.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatHourMinute(hour, minute),
                    fontSize = 52.sp,
                    style = MaterialTheme.typography.displayMedium,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = if (!enabled) {
                        "alarm off"
                    } else if (windowMinutes > 0) {
                        "from ${earliestLabel(hour, minute, windowMinutes)}"
                    } else {
                        "exactly"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (enabled && windowMinutes > 0) {
                        windowColor
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HalfChip("AM", selected = !isPm, enabled = enabled) {
                if (isPm) onChange(hour - 12, minute)
            }
            HalfChip("PM", selected = isPm, enabled = enabled) {
                if (!isPm) onChange(hour + 12, minute)
            }
        }
    }
}

@Composable
private fun DialLabel(text: String, modifier: Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
private fun HalfChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Turns a touch anywhere in the dial into a time. Distance from the centre is ignored on purpose:
 * only the angle matters, so a lazy thumb nowhere near the ring still works.
 */
private fun emit(
    position: Offset,
    size: IntSize,
    isPm: Boolean,
    onChange: (Int, Int) -> Unit
) {
    val dx = position.x - size.width / 2f
    val dy = position.y - size.height / 2f
    if (dx == 0f && dy == 0f) return

    // atan2 with y flipped puts zero at the top and increases clockwise, like a clock face.
    var degrees = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()
    if (degrees < 0f) degrees += 360f

    val snapped = ((degrees / 360f * 720f).roundToInt() / 5) * 5 % 720
    val half = if (isPm) 12 else 0
    onChange(half + (snapped / 60) % 12, snapped % 60)
}

/** The earliest the alarm might go off, for the caption under the time. */
private fun earliestLabel(hour: Int, minute: Int, windowMinutes: Int): String {
    val total = (hour * 60 + minute - windowMinutes + 24 * 60) % (24 * 60)
    return formatHourMinute(total / 60, total % 60)
}
