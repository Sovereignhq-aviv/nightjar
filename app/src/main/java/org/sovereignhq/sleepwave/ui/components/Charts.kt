package org.sovereignhq.sleepwave.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.sovereignhq.sleepwave.data.Sample
import org.sovereignhq.sleepwave.data.Stage
import org.sovereignhq.sleepwave.ui.theme.DataColors
import kotlin.math.max

/**
 * The overnight sleep graph.
 *
 * Depth runs downward, so the silhouette reads the way people already expect: dips are deep sleep,
 * peaks are awake. Under it sits a colour ribbon giving the exact stage minute by minute, because at
 * 480 minutes across a phone screen the silhouette alone cannot answer "was I awake at 3am?".
 */
@Composable
fun Hypnogram(
    samples: List<Sample>,
    modifier: Modifier = Modifier,
    showSnoring: Boolean = true
) {
    if (samples.isEmpty()) return
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier.fillMaxWidth().height(160.dp)) {
        val ribbonHeight = 10f * density
        val snoreLane = if (showSnoring) 6f * density else 0f
        val plotHeight = size.height - ribbonHeight - snoreLane - 4f * density
        val step = size.width / max(1, samples.size - 1)

        fun levelOf(stage: Int): Float = when (stage) {
            Stage.AWAKE.ordinal -> 0.03f
            Stage.LIGHT.ordinal -> 0.45f
            else -> 0.88f
        }

        var minute = 60
        while (minute < samples.size) {
            val x = minute * step
            drawLine(
                color = gridColor,
                start = Offset(x, 0f),
                end = Offset(x, plotHeight),
                strokeWidth = 1f
            )
            minute += 60
        }

        val fill = Path().apply {
            moveTo(0f, 0f)
            samples.forEachIndexed { i, s -> lineTo(i * step, levelOf(s.stage) * plotHeight) }
            lineTo(size.width, 0f)
            close()
        }
        drawPath(
            path = fill,
            brush = Brush.verticalGradient(
                0f to DataColors.stageLight.copy(alpha = 0.38f),
                1f to DataColors.stageLight.copy(alpha = 0.04f),
                startY = 0f,
                endY = plotHeight
            )
        )

        val line = Path().apply {
            samples.forEachIndexed { i, s ->
                val x = i * step
                val y = levelOf(s.stage) * plotHeight
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        drawPath(path = line, color = DataColors.stageLight, style = Stroke(width = 2f * density))

        val ribbonTop = size.height - ribbonHeight
        val barWidth = max(1f, step + 1f)
        samples.forEachIndexed { i, s ->
            drawRect(
                color = when (s.stage) {
                    Stage.AWAKE.ordinal -> DataColors.stageAwake
                    Stage.LIGHT.ordinal -> DataColors.stageLight
                    else -> DataColors.stageDeep
                },
                topLeft = Offset(i * step, ribbonTop),
                size = Size(barWidth, ribbonHeight)
            )
        }

        if (showSnoring) {
            val snoreTop = ribbonTop - snoreLane
            samples.forEachIndexed { i, s ->
                if (s.snoring) {
                    drawRect(
                        color = DataColors.snore,
                        topLeft = Offset(i * step, snoreTop),
                        size = Size(barWidth, snoreLane * 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun HypnogramAxis(startLabel: String, endLabel: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            startLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            endLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun StageLegend(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendDot(DataColors.stageAwake, "Awake")
        LegendDot(DataColors.stageLight, "Light")
        LegendDot(DataColors.stageDeep, "Deep")
    }
}

/**
 * Sleep quality as a ring. The sweep animates in once on arrival, which is the one place a reveal
 * earns its keep: the number is the answer to "how did I sleep", so it gets a beat of attention.
 */
@Composable
fun ScoreRing(score: Int, modifier: Modifier = Modifier, diameter: Int = 128) {
    val target = score.coerceIn(0, 100) / 100f
    val sweep by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 620),
        label = "scoreSweep"
    )
    val track = MaterialTheme.colorScheme.outlineVariant

    Box(modifier.size(diameter.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 11f * density
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = track,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        DataColors.stageDeep,
                        DataColors.stageLight,
                        DataColors.snore,
                        DataColors.stageDeep
                    )
                ),
                startAngle = 135f,
                sweepAngle = 270f * sweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score%", style = MaterialTheme.typography.displayMedium)
            Text(
                "quality",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

data class Bar(val label: String, val value: Float, val highlight: Boolean = false)

@Composable
fun TrendBars(
    bars: List<Bar>,
    modifier: Modifier = Modifier,
    goalValue: Float? = null,
    barColor: Color = DataColors.stageLight,
    heightDp: Int = 130
) {
    if (bars.isEmpty()) return
    val maxValue = max(0.0001f, max(bars.maxOf { it.value }, goalValue ?: 0f))
    val guideColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(heightDp.dp)) {
            val slot = size.width / bars.size
            val barW = slot * 0.56f
            val radius = barW / 2f

            goalValue?.let { g ->
                val y = size.height * (1f - (g / maxValue).coerceIn(0f, 1f))
                var x = 0f
                while (x < size.width) {
                    drawLine(
                        color = guideColor.copy(alpha = 0.45f),
                        start = Offset(x, y),
                        end = Offset(x + 6f, y),
                        strokeWidth = 1.5f
                    )
                    x += 12f
                }
            }

            bars.forEachIndexed { i, bar ->
                val h = size.height * (bar.value / maxValue).coerceIn(0f, 1f)
                if (h <= 0f) return@forEachIndexed
                val left = i * slot + (slot - barW) / 2f
                drawRoundRect(
                    color = if (bar.highlight) barColor else barColor.copy(alpha = 0.5f),
                    topLeft = Offset(left, size.height - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(radius, radius)
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            bars.forEach { bar ->
                Text(
                    text = bar.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** The live trace on the night screen: what the microphone has heard so far. */
@Composable
fun LiveWave(activity: List<Float>, modifier: Modifier = Modifier) {
    val idleColor = MaterialTheme.colorScheme.outlineVariant
    val traceColor = MaterialTheme.colorScheme.primary

    Canvas(modifier.fillMaxWidth().height(72.dp)) {
        if (activity.isEmpty()) {
            drawLine(
                color = idleColor,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 1.5f
            )
            return@Canvas
        }
        val step = size.width / max(1, activity.size - 1)
        val path = Path()
        activity.forEachIndexed { i, v ->
            val x = i * step
            val y = size.height - (v.coerceIn(0f, 1f) * size.height * 0.92f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = traceColor, style = Stroke(width = 2f * density))
    }
}
