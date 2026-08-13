package org.sovereignhq.sleepwave.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The app's signature motif, used at three scales: a thumbnail beside every recording, full width
 * in the player, and stretched across the whole night on the sleep screen.
 *
 * Bars are mirrored around the centre line, which is what makes a 28dp-tall thumbnail still read as
 * audio rather than as a bar chart. The envelope arrives pre-computed from the recorder, so drawing
 * a hundred of these costs no file access at all.
 */
@Composable
fun Waveform(
    envelope: List<Int>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    /** 0..1 playhead. Negative hides it and draws the whole shape at full strength. */
    progress: Float = -1f,
    bars: Int = 44,
    minBarHeightFraction: Float = 0.06f,
    onSeek: ((Float) -> Unit)? = null
) {
    val buckets = remember(envelope, bars) { resample(envelope, bars) }
    val unplayed = color.copy(alpha = 0.32f)

    var interaction: Modifier = modifier
    if (onSeek != null) {
        interaction = interaction
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (size.width > 0) onSeek(offset.x / size.width)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    if (size.width > 0) onSeek(change.position.x / size.width)
                }
            }
    }

    Canvas(modifier = interaction) {
        if (buckets.isEmpty()) {
            drawLine(
                color = unplayed,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = max(1f, 1.5f * density)
            )
            return@Canvas
        }

        val slot = size.width / buckets.size
        val barWidth = max(1.4f * density, slot * 0.62f)
        val radius = barWidth / 2f
        val centre = size.height / 2f
        val playedUpTo = if (progress >= 0f) progress.coerceIn(0f, 1f) * buckets.size else Float.MAX_VALUE

        buckets.forEachIndexed { i, value ->
            val fraction = (value / 100f).coerceIn(minBarHeightFraction, 1f)
            val half = fraction * centre
            val x = i * slot + (slot - barWidth) / 2f
            drawRoundRect(
                color = if (i < playedUpTo) color else unplayed,
                topLeft = Offset(x, centre - half),
                size = Size(barWidth, half * 2f),
                cornerRadius = CornerRadius(radius, radius)
            )
        }
    }
}

/**
 * How loud an event was, relative to the room it happened in. Capped at 40dB above the background,
 * which is roughly the difference between a quiet bedroom and a shout.
 */
@Composable
fun LoudnessBar(
    peakAboveFloorDb: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val fraction = (peakAboveFloorDb / 40f).coerceIn(0.04f, 1f)
    val track = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier.fillMaxWidth()) {
        val h = size.height
        val r = h / 2f
        drawRoundRect(
            color = track,
            topLeft = Offset(0f, 0f),
            size = Size(size.width, h),
            cornerRadius = CornerRadius(r, r)
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, 0f),
            size = Size(size.width * fraction, h),
            cornerRadius = CornerRadius(r, r)
        )
    }
}

/**
 * Squashes or stretches an envelope to [target] buckets. Peak-preserving when downsampling, so a
 * thumbnail keeps the transient that makes the clip recognisable instead of averaging it away.
 */
private fun resample(source: List<Int>, target: Int): List<Int> {
    if (source.isEmpty() || target <= 0) return emptyList()
    if (source.size == target) return source
    if (source.size < target) {
        return (0 until target).map { i ->
            source[(i.toFloat() / target * source.size).toInt().coerceAtMost(source.lastIndex)]
        }
    }
    val perBucket = source.size.toFloat() / target
    return (0 until target).map { i ->
        val from = (i * perBucket).roundToInt().coerceIn(0, source.lastIndex)
        val to = ((i + 1) * perBucket).roundToInt().coerceIn(from + 1, source.size)
        var peak = 0
        for (j in from until to) if (source[j] > peak) peak = source[j]
        peak
    }
}
