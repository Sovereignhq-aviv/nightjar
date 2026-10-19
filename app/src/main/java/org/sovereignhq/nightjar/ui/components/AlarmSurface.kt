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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.util.Locale
import org.sovereignhq.nightjar.alarm.MathPuzzle
import org.sovereignhq.nightjar.alarm.PlaceEmbedder
import org.sovereignhq.nightjar.alarm.PlaceMatch
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
 * Switching off runs in a fixed order: the check away from the bed if one is registered (a barcode,
 * a photo of a place, or a short dance), then the sums if any are set. The check comes first because it is the step
 * that gets you out of bed.
 */
@Composable
fun AlarmSurface(
    puzzleDigits: Int,
    puzzleCount: Int,
    wakeCode: String,
    wakePhotoRefs: String,
    wakePhotoThreshold: Float,
    wakeDance: Boolean,
    quietUntilMs: Long,
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

    val needsPhoto = wakePhotoRefs.isNotBlank()
    val needsScan = !needsPhoto && wakeCode.isNotBlank()
    val needsDance = !needsPhoto && !needsScan && wakeDance
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
                quietUntilMs = quietUntilMs,
                onQuieten = onQuieten,
                onScanned = afterScan,
                onCannotScan = { step = OffStep.FALLBACK },
                onGiveUp = { step = OffStep.HOME }
            )

            OffStep.PHOTO -> PhotoGate(
                references = wakePhotoRefs,
                threshold = wakePhotoThreshold,
                quietUntilMs = quietUntilMs,
                onQuieten = onQuieten,
                onMatched = afterScan,
                onCannotMatch = { step = OffStep.FALLBACK },
                onGiveUp = { step = OffStep.HOME }
            )

            OffStep.DANCE -> DanceGate(
                quietUntilMs = quietUntilMs,
                onQuieten = onQuieten,
                onDanced = afterScan,
                onCannotDance = { step = OffStep.FALLBACK },
                onGiveUp = { step = OffStep.HOME }
            )

            OffStep.SUMS -> PuzzleGate(
                digits = puzzleDigits,
                count = puzzleCount,
                onKeyPress = onQuieten,
                onSolved = onDismiss,
                onGiveUp = { step = OffStep.HOME }
            )

            // The way out when the camera check cannot be done - a hotel room, a broken camera, a
            // product that ran out. One two-digit sum: enough to need you awake, no more. It began as
            // five three-digit sums, then three, and both were a punishment for the camera's failure
            // rather than yours. Walking to the check is what gets you up; this only has to exist.
            OffStep.FALLBACK -> PuzzleGate(
                digits = FALLBACK_DIGITS,
                count = 1,
                onKeyPress = onQuieten,
                onSolved = onDismiss,
                onGiveUp = {
                    step = when {
                        needsPhoto -> OffStep.PHOTO
                        needsDance -> OffStep.DANCE
                        else -> OffStep.SCAN
                    }
                }
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
                    text = describeSwitchOff(needsScan, needsPhoto, needsDance, puzzleDigits, puzzleCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(40.dp))

                Button(
                    onClick = {
                        when {
                            needsPhoto -> { onQuieten(); step = OffStep.PHOTO }
                            needsScan -> { onQuieten(); step = OffStep.SCAN }
                            needsDance -> { onQuieten(); step = OffStep.DANCE }
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
                        text = if (needsScan || needsPhoto || needsDance || puzzleDigits > 0) "Wake me up" else "I'm up",
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
                QuietButton(quietUntilMs = quietUntilMs, onQuieten = onQuieten)
            }
        }
    }
}

private enum class OffStep { HOME, SCAN, PHOTO, DANCE, SUMS, FALLBACK }

/** Two digits: mental arithmetic, not a test. */
private const val FALLBACK_DIGITS = 2

private fun describeSwitchOff(
    needsScan: Boolean,
    needsPhoto: Boolean,
    needsDance: Boolean,
    digits: Int,
    count: Int
): String {
    val sums = if (digits > 0) PuzzleGenerator.describe(digits, count).lowercase() else null
    val away = when {
        needsPhoto -> "the camera pointed at your place"
        needsScan -> "your wake code scanned"
        needsDance -> "three dance moves in front of the camera"
        else -> null
    }
    val switchOff = when {
        away != null && sums != null -> "Switching it off needs $away, then $sums."
        away != null -> "Switching it off needs $away."
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
    quietUntilMs: Long,
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
                        // Pointing the camera at the wrong code still means you are up and trying,
                        // so it buys more quiet - once per mistake, not once per camera frame.
                        !wrongCode -> { wrongCode = true; onQuieten() }
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
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )

        Spacer(Modifier.height(10.dp))
        QuietButton(quietUntilMs = quietUntilMs, onQuieten = onQuieten)
        Spacer(Modifier.height(20.dp))

        // A real button, well apart from Back: side by side as two text links, a sleepy thumb hit
        // whichever was nearer.
        OutlinedButton(
            onClick = onCannotScan,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(
                "Can't scan it? Do sums instead",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        TextAction("Back", onGiveUp)
    }
}

/**
 * Point the camera at the registered place. It has to match on several frames in a row, so one
 * lucky frame of a similar-looking wall does not count.
 */
@Composable
private fun PhotoGate(
    references: String,
    threshold: Float,
    quietUntilMs: Long,
    onQuieten: () -> Unit,
    onMatched: () -> Unit,
    onCannotMatch: () -> Unit,
    onGiveUp: () -> Unit
) {
    val context = LocalContext.current
    val cameraAllowed = remember { hasCameraPermission(context) }
    val refs = remember(references) { PlaceMatch.decode(references) }
    val embedder = remember { PlaceEmbedder.create(context) }
    DisposableEffect(embedder) { onDispose { embedder?.close() } }
    var score by remember { mutableFloatStateOf(0f) }
    var streak by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(false) }
    val usable = cameraAllowed && embedder != null && refs.isNotEmpty()

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Go and point the camera at your place",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))

        if (usable) {
            PlaceCamera(
                onFrame = { frame ->
                    if (done) return@PlaceCamera
                    val live = embedder?.embed(frame) ?: return@PlaceCamera
                    val now = PlaceMatch.bestMatch(live, refs)
                    score = now
                    streak = if (now >= threshold) streak + 1 else 0
                    if (streak >= MATCH_FRAMES && !done) {
                        done = true
                        ContextCompat.getMainExecutor(context).execute { onMatched() }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(18.dp))
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { (score / threshold).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (score >= threshold) "That's it - hold still" else "Closer to how you took the photos",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        } else {
            Text(
                if (!cameraAllowed) {
                    "Nightjar is not allowed to use the camera any more, so it cannot check the place. " +
                        "Use the sums instead, and allow the camera again in Settings later."
                } else {
                    "The photo check is not working on this phone right now. Use the sums instead."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(10.dp))
        QuietButton(quietUntilMs = quietUntilMs, onQuieten = onQuieten)
        Spacer(Modifier.height(20.dp))

        OutlinedButton(
            onClick = onCannotMatch,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(
                "Won't match? Do sums instead",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        TextAction("Back", onGiveUp)
    }
}

/** About a second of agreement at the camera's 400ms frame rate. */
private const val MATCH_FRAMES = 3

/**
 * Three moves in front of the selfie camera. Each finished move buys more silence, since you are
 * plainly up and doing it.
 */
@Composable
private fun DanceGate(
    quietUntilMs: Long,
    onQuieten: () -> Unit,
    onDanced: () -> Unit,
    onCannotDance: () -> Unit,
    onGiveUp: () -> Unit
) {
    val context = LocalContext.current
    val cameraAllowed = remember { hasCameraPermission(context) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (cameraAllowed) {
            DanceRoutine(onDone = onDanced, onProgress = onQuieten, modifier = Modifier.fillMaxWidth())
        } else {
            Text(
                "Nightjar is not allowed to use the camera any more, so it cannot see the dance. " +
                    "Use the sum instead, and allow the camera again in Settings later.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(10.dp))
        QuietButton(quietUntilMs = quietUntilMs, onQuieten = onQuieten)
        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = onCannotDance,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(
                "Can't dance now? Do a sum instead",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        TextAction("Back", onGiveUp)
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
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp)
    )
}

/**
 * Silences the room without ending the alarm, and says for how long.
 *
 * It stays pressable while quiet: each press restarts the grace period, which is what makes the walk
 * to a bathroom code survivable. The countdown is the point - without it the sound came back as a
 * surprise, usually after you had put the phone down somewhere.
 */
@Composable
private fun QuietButton(quietUntilMs: Long, onQuieten: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(quietUntilMs) {
        while (quietUntilMs > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
        now = System.currentTimeMillis()
    }
    val secondsLeft = ((quietUntilMs - now + 999) / 1_000).coerceAtLeast(0)
    val quiet = secondsLeft > 0

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(role = Role.Button, onClick = onQuieten)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.VolumeOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (quiet) {
                    "Silent for ${secondsLeft / 60}:${String.format(Locale.ROOT, "%02d", secondsLeft % 60)}"
                } else {
                    "Quiet it"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (quiet) {
                "Tap again for more time. The sound comes back when this runs out."
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
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )

        Spacer(Modifier.height(14.dp))

        Keypad(
            onAnyKey = onKeyPress,
            onDigit = { d ->
                val next = entry + d
                when (puzzle.check(next)) {
                    MathPuzzle.Verdict.CORRECT -> {
                        entry = ""
                        wrong = false
                        if (index + 1 >= puzzles.size) onSolved() else index++
                    }
                    // Wrong answers clear straight away, with the message, so the next attempt
                    // starts from a blank line rather than from deleting the last one.
                    MathPuzzle.Verdict.WRONG -> {
                        entry = ""
                        wrong = true
                    }
                    MathPuzzle.Verdict.NOT_YET -> {
                        entry = next
                        wrong = false
                    }
                }
            },
            onBackspace = { entry = entry.dropLast(1); wrong = false },
            onClear = { entry = ""; wrong = false }
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
    onClear: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("back", "0", "clear")
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
                    val isClear = key == "clear"
                    val isBack = key == "back"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1.45f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(role = Role.Button) {
                                onAnyKey()
                                when {
                                    isClear -> onClear()
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

                            isClear -> Text(
                                "Clear",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
