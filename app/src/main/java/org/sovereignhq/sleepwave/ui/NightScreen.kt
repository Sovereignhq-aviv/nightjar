package org.sovereignhq.sleepwave.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.sovereignhq.sleepwave.ui.components.LiveWave
import org.sovereignhq.sleepwave.ui.components.SectionLabel

/**
 * What you see if you glance at the phone at 3am. Almost nothing, at almost no brightness.
 *
 * Screen brightness is forced to its minimum while this is showing: a bright phone in a dark
 * bedroom defeats the purpose of the app it belongs to. The one moving thing is a slow pulse on the
 * listening dot, which exists to answer the only question worth asking at 3am - is this still on.
 */
@Composable
fun NightScreen(
    startedAtMs: Long,
    alarmTargetMs: Long,
    liveActivity: List<Float>,
    level: Float,
    snoreMinutes: Int,
    clipCount: Int,
    error: String?,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var confirming by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(20_000)
        }
    }

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        val previous = window?.attributes?.screenBrightness
        window?.let {
            it.attributes = it.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF + 0.02f
            }
        }
        onDispose {
            window?.let {
                it.attributes = it.attributes.apply {
                    screenBrightness = previous ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }

    val pulse by rememberInfiniteTransition(label = "listening").animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val elapsedMinutes = ((now - startedAtMs) / 60_000L).toInt().coerceAtLeast(0)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(
                            alpha = maxOf(pulse, level.coerceIn(0f, 1f))
                        )
                    )
            )
            Text(
                "  LISTENING",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = formatClock(now),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "in bed for ${formatDuration(elapsedMinutes)}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(36.dp))

        LiveWave(liveActivity)

        Spacer(Modifier.height(36.dp))

        if (alarmTargetMs > 0) {
            SectionLabel("Alarm")
            Text(
                text = "by ${formatClock(alarmTargetMs)}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.secondary
            )
        } else {
            Text(
                "No alarm set",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (clipCount > 0 || snoreMinutes > 0) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = buildString {
                    if (clipCount > 0) {
                        append("$clipCount recording${if (clipCount == 1) "" else "s"} so far")
                    }
                    if (snoreMinutes > 0) {
                        if (isNotEmpty()) append("  ·  ")
                        append("$snoreMinutes min snoring")
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(48.dp))

        OutlinedButton(
            onClick = { confirming = true },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text("Stop tracking", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Stop tracking?") },
            text = {
                Text(
                    "The night so far will be saved and the alarm will be cancelled.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onStop()
                }) { Text("Stop", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("Keep going") }
            }
        )
    }
}
