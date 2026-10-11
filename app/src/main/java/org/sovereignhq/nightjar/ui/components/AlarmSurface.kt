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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.sovereignhq.nightjar.alarm.MathPuzzle
import org.sovereignhq.nightjar.alarm.PuzzleGenerator
import org.sovereignhq.nightjar.alarm.WakeCode
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
 * so silencing the room is never the same thing as switching the alarm off. Starting to switch the
 * alarm off presses it for you: nobody else in the room should have to listen while you do sums.
 *
 * Switching off runs in a fixed order: scan the wake code if one is registered, then the sums if any
 * are set. The scan comes first because it is the step that gets you out of bed.
 */
@Composable
fun AlarmSurface(
    puzzleDigits: Int,
    puzzleCount: Int,
    wakeCode: String,
    quietened: Boolean,
    onQuieten: () -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableStateOf(OffStep.HOME) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(5_000)
        }
    }

    val needsScan = wakeCode.isNotBlank()
    val afterScan = { if (puzzleDigits > 0) step = OffStep.SUMS else onDismiss() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (step) {
            OffStep.SCAN -> ScanGate(
                wakeCode = wakeCode,
                quietened = quietened,
                onQuieten = onQuieten,
                onScanned = afterScan,
                onCannotScan = { step = OffStep.FALLBACK },
                onGiveUp = { step = OffStep.HOME }
            )

            OffStep.SUMS -> PuzzleGate(
                digits = puzzleDigits,
                count = puzzleCount,
                onKeyPress = onQuieten,
                onSolved = onDismiss,
                onGiveUp = { step = OffStep.HOME }
            )

            // The way out when the code cannot be scanned - a hotel room, a broken camera. It has to
            // cost more than walking to the bathroom would, or it becomes the way everyone switches
            // the alarm off.
            OffStep.FALLBACK -> PuzzleGate(
                digits = 3,
                count = PuzzleGenerator.MAX_PUZZLES,
                onKeyPress = onQuieten,
                onSolved = onDismiss,
                onGiveUp = { step = OffStep.SCAN }
            )

            OffStep.HOME -> {
                Text(
                    "Good morning",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(formatClock(now), style = MaterialTheme.typography.displayLarge)

                Spacer(Modifier.height(10.dp))
                Text(
                    text = describeSwitchOff(needsScan, puzzleDigits, puzzleCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(40.dp))

                Button(
                    onClick = {
                        when {
                            needsScan -> { onQuieten(); step = OffStep.SCAN }
                            puzzleDigits > 0 -> { onQuieten(); step = OffStep.SUMS }
                            else -> onDismiss()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(66.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        text = if (needsScan || puzzleDigits > 0) "Wake me up" else "I'm up",
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
}

private enum class OffStep { HOME, SCAN, SUMS, FALLBACK }

private fun describeSwitchOff(needsScan: Boolean, digits: Int, count: Int): String {
    val sums = if (digits > 0) PuzzleGenerator.describe(digits, count).lowercase() else null
    val switchOff = when {
        needsScan && sums != null -> "Switching it off needs your wake code scanned, then $sums."
        needsScan -> "Switching it off needs your wake code scanned."
        sums != null -> "Switching it off needs $sums."
        else -> null
    }
    val keys = "Either volume button silences it for a while."
    return if (switchOff == null) keys else "$switchOff $keys"
}

/**
 * Point the camera at the registered code. Anything else in view is ignored, so a cereal box by the
 * bed does not count.
 */
@Composable
private fun ScanGate(
    wakeCode: String,
    quietened: Boolean,
    onQuieten: () -> Unit,
    onScanned: () -> Unit,
    onCannotScan: () -> Unit,
    onGiveUp: () -> Unit
) {
    val context = LocalContext.current
    val cameraAllowed = remember { hasCameraPermission(context) }
    var wrongCode by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Go and scan your wake code",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))

        if (cameraAllowed) {
            CodeScanner(
                onCode = { scanned ->
                    when {
                        done -> Unit
                        WakeCode.matches(scanned, wakeCode) -> { done = true; onScanned() }
                        else -> wrongCode = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(18.dp))
            )
        } else {
            Text(
                "Nightjar is not allowed to use the camera any more, so it cannot read the code. " +
                    "Use the sums instead, and allow the camera again in Settings later.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        Text(
            text = if (wrongCode) "That is a different code." else " ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(Modifier.height(10.dp))
        QuietButton(quietened = quietened, onQuieten = onQuieten)
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextAction("Back", onGiveUp)
            TextAction("Can't scan it?", onCannotScan)
        }
    }
}

@Composable
private fun TextAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    )
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
    onKeyPress: () -> Unit,
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
            onAnyKey = onKeyPress,
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

        TextAction("Back", onGiveUp)
    }
}

/**
 * A keypad of its own rather than a text field with a number keyboard. Soft keyboards over a
 * lock-screen window are unreliable, and a 72dp target is a lot easier to hit than a keyboard key
 * when you have one eye open.
 */
@Composable
private fun Keypad(
    onAnyKey: () -> Unit,
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
                                onAnyKey()
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
