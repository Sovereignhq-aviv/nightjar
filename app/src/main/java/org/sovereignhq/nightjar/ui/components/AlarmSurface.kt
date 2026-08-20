package org.sovereignhq.nightjar.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.sovereignhq.nightjar.alarm.MathPuzzle
import org.sovereignhq.nightjar.alarm.PuzzleGenerator
import org.sovereignhq.nightjar.ui.formatClock

/**
 * The wake-up screen, shared by the full-screen alarm activity and the main app.
 *
 * Sharing it is the fix for a real hole: Android 14 can refuse an app permission to show a
 * full-screen activity over the lock screen, and when that happened the notification shade was the
 * only place with a stop button. Now opening the app during a ringing alarm puts the same controls
 * on screen, so there is always a button.
 *
 * "Quiet it" exists because you may not be the only person in the bed. It drops the sound
 * immediately, before any puzzle is solved, and the sound returns on its own if you then do nothing -
 * so silencing the room is never the same thing as switching the alarm off.
 */
@Composable
fun AlarmSurface(
    puzzleDigits: Int,
    puzzleCount: Int,
    quietened: Boolean,
    onQuieten: () -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var solving by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(5_000)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (solving) {
            PuzzleGate(
                digits = puzzleDigits,
                count = puzzleCount,
                onSolved = onDismiss,
                onGiveUp = { solving = false }
            )
        } else {
            Text(
                "Good morning",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(formatClock(now), style = MaterialTheme.typography.displayLarge)

            Spacer(Modifier.height(10.dp))
            Text(
                text = if (puzzleDigits > 0) {
                    "Switching it off needs ${PuzzleGenerator.describe(puzzleDigits, puzzleCount).lowercase()}."
                } else {
                    "Press either volume button to snooze."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(40.dp))

            Button(
                onClick = { if (puzzleDigits > 0) solving = true else onDismiss() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(66.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(
                    text = if (puzzleDigits > 0) "Wake me up" else "I'm up",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = onSnooze,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(
                    "Snooze",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(24.dp))
            QuietButton(quietened = quietened, onQuieten = onQuieten)
        }
    }
}

/** Silences the room without ending the alarm. */
@Composable
private fun QuietButton(quietened: Boolean, onQuieten: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (quietened) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
                .clickable(enabled = !quietened, onClick = onQuieten)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.VolumeOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(20.dp)
            )
            Text(
                text = if (quietened) "  Sound off" else "  Quiet it",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (quietened) {
                "The sound comes back shortly unless you switch the alarm off."
            } else {
                "Silences it now without switching the alarm off, so you can get up without waking " +
                    "anyone else."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PuzzleGate(
    digits: Int,
    count: Int,
    onSolved: () -> Unit,
    onGiveUp: () -> Unit
) {
    val puzzles: List<MathPuzzle> = remember(digits, count) {
        PuzzleGenerator.generateSet(digits, count)
    }
    var index by remember { mutableIntStateOf(0) }
    var entry by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    val puzzle = puzzles.getOrNull(index) ?: return
    val nudge by rememberInfiniteTransition(label = "nudge").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_400), repeatMode = RepeatMode.Reverse),
        label = "nudgeAlpha"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (puzzles.size > 1) {
            Text(
                "${index + 1} of ${puzzles.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
        }

        Text(
            text = puzzle.question,
            fontSize = 46.sp,
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(14.dp))

        Text(
            text = entry.ifBlank { "?" },
            fontSize = 40.sp,
            style = MaterialTheme.typography.displayMedium,
            color = when {
                wrong -> MaterialTheme.colorScheme.error
                entry.isBlank() -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.alpha(if (entry.isBlank()) nudge else 1f)
        )

        Text(
            text = if (wrong) "Not quite. Try again." else " ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(Modifier.height(14.dp))

        Keypad(
            onDigit = { d ->
                if (entry.length < 6) {
                    entry += d
                    wrong = false
                }
            },
            onBackspace = { entry = entry.dropLast(1); wrong = false },
            onSubmit = {
                val given = entry.toIntOrNull()
                when {
                    given == null -> wrong = true
                    given == puzzle.answer -> {
                        entry = ""
                        wrong = false
                        if (index + 1 >= puzzles.size) onSolved() else index++
                    }
                    else -> {
                        wrong = true
                        entry = ""
                    }
                }
            }
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Back",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onGiveUp)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        )
    }
}

/**
 * A keypad of its own rather than a text field with a number keyboard. Soft keyboards over a
 * lock-screen window are unreliable, and a 72dp target is a lot easier to hit than a keyboard key
 * when you have one eye open.
 */
@Composable
private fun Keypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("back", "0", "ok")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { key ->
                    val isOk = key == "ok"
                    val isBack = key == "back"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1.45f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                when {
                                    isOk -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                            .clickable {
                                when {
                                    isOk -> onSubmit()
                                    isBack -> onBackspace()
                                    else -> onDigit(key)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            isBack -> Icon(
                                Icons.Rounded.Backspace,
                                contentDescription = "Delete a digit",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            isOk -> Text(
                                "Enter",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimary
                            )

                            else -> Text(
                                key,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
