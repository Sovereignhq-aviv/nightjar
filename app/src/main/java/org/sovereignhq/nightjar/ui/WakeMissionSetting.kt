package org.sovereignhq.nightjar.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.sovereignhq.nightjar.alarm.PlaceEmbedder
import org.sovereignhq.nightjar.alarm.PlaceMatch
import org.sovereignhq.nightjar.alarm.WakeCode
import org.sovereignhq.nightjar.ui.components.CodeScanner
import org.sovereignhq.nightjar.ui.components.PlaceCamera
import org.sovereignhq.nightjar.ui.components.SectionLabel
import org.sovereignhq.nightjar.ui.components.hasCameraPermission

private enum class Mission(val label: String) { OFF("Off"), CODE("Barcode"), PHOTO("Photo") }

/**
 * What you have to do, away from the bed, before the alarm will switch off: scan a barcode, or point
 * the camera at a place. One or the other - two checks in a row would just be a longer walk.
 *
 * Registering is the moment to ask for the camera, while the app is open and in your hand. Asking at
 * the alarm itself would put a permission dialog in front of someone half asleep, over a lock screen
 * that may not even show it.
 */
@Composable
fun WakeMissionSetting(
    wakeCode: String,
    wakePhotoRefs: String,
    onCode: (String) -> Unit,
    onPhoto: (refs: String, threshold: Float) -> Unit,
    onOff: () -> Unit
) {
    val registered = when {
        wakePhotoRefs.isNotBlank() -> Mission.PHOTO
        wakeCode.isNotBlank() -> Mission.CODE
        else -> Mission.OFF
    }
    var shown by remember(registered) { mutableStateOf(registered) }
    var confirmingOff by remember { mutableStateOf(false) }

    SectionLabel("Get out of bed to switch it off")
    Spacer(Modifier.height(6.dp))
    Text(
        "The alarm only switches off once you have walked somewhere and proved it: scan a barcode " +
            "that lives there, or point the camera at the place itself. The room goes silent while " +
            "you do. If the barcode goes missing or the place looks different, three sums at your usual " +
            "difficulty always get you out.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(10.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Mission.entries.forEach { mission ->
            val selected = shown == mission
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable(role = Role.Tab) {
                        if (mission == Mission.OFF && registered != Mission.OFF) confirmingOff = true
                        else shown = mission
                    }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    mission.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))

    when (shown) {
        Mission.OFF -> Unit
        Mission.CODE -> CodeMission(wakeCode = wakeCode, onChange = onCode)
        Mission.PHOTO -> PhotoMission(registered = wakePhotoRefs, onRegistered = onPhoto)
    }

    if (registered != Mission.OFF && shown != registered && shown != Mission.OFF) {
        Spacer(Modifier.height(6.dp))
        Text(
            "Your ${registered.label.lowercase()} stays in charge until this one is registered.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (confirmingOff) {
        AlertDialog(
            onDismissRequest = { confirmingOff = false },
            title = { Text("Turn off the check?") },
            text = {
                Text(
                    "The alarm will switch off without getting out of bed. Turning it back on means " +
                        "registering again.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmingOff = false; shown = Mission.OFF; onOff() }) {
                    Text("Turn off", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingOff = false }) { Text("Keep it") }
            }
        )
    }
}

@Composable
private fun rememberCameraGate(onGranted: () -> Unit): Pair<() -> Unit, Boolean> {
    val context = LocalContext.current
    var refused by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refused = !it
        if (it) onGranted()
    }
    val open = {
        if (hasCameraPermission(context)) onGranted() else launcher.launch(Manifest.permission.CAMERA)
    }
    return open to refused
}

@Composable
private fun CameraRefused() {
    Spacer(Modifier.height(6.dp))
    Text(
        "Without the camera Nightjar cannot check anything. Allow it in Android's settings for " +
            "Nightjar, then try again.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error
    )
}

@Composable
private fun CodeMission(wakeCode: String, onChange: (String) -> Unit) {
    var scanning by remember { mutableStateOf(false) }
    // What the camera read, waiting for a yes. Saving the first code in view meant a barcode on
    // the shelf behind the one you meant could become the only way to switch the alarm off.
    var pending by remember { mutableStateOf<String?>(null) }
    val (openCamera, refused) = rememberCameraGate { scanning = true }

    when {
        scanning -> {
            CodeScanner(
                onCode = { code ->
                    if (scanning && code.isNotBlank()) {
                        scanning = false
                        pending = code.trim()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { scanning = false },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("Cancel") }
        }

        pending != null -> {
            Text(
                "Read: ${WakeCode.preview(pending.orEmpty())}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Make sure this is the code you will leave there, not something else that was in view.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { pending = null; scanning = true },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("Scan again") }
                Button(
                    onClick = { onChange(pending.orEmpty()); pending = null },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("Use this code") }
            }
        }

        wakeCode.isNotBlank() -> {
            Text(
                "Registered: ${WakeCode.preview(wakeCode)}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = openCamera,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("Scan a new one") }
        }

        else -> {
            Text(
                "Any QR code or product barcode works - shampoo, toothpaste. Pick something you own " +
                    "only one of, or the spare by the bed counts too.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = openCamera,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("Register a barcode") }
        }
    }

    if (refused) CameraRefused()
}

private val PHOTO_HINTS = listOf(
    "Stand where you will stand in the morning, light on.",
    "Now one step closer.",
    "Now half a step to the side."
)

/**
 * Three photos of one place, then a live meter to prove the bar is right before trusting a morning
 * to it: the place itself should pass, and the bed should not.
 */
@Composable
private fun PhotoMission(registered: String, onRegistered: (refs: String, threshold: Float) -> Unit) {
    val context = LocalContext.current
    var capturing by remember { mutableStateOf(false) }
    var trying by remember { mutableStateOf(false) }
    var taken by remember { mutableStateOf(listOf<FloatArray>()) }
    var latest by remember { mutableStateOf<FloatArray?>(null) }
    var score by remember { mutableFloatStateOf(0f) }
    val cameraOn = capturing || trying
    val (openCamera, refused) = rememberCameraGate { taken = emptyList(); capturing = true }

    val embedder = remember(cameraOn) { if (cameraOn) PlaceEmbedder.create(context) else null }
    DisposableEffect(embedder) { onDispose { embedder?.close() } }

    val references = remember(registered) { PlaceMatch.decode(registered) }
    val bar = remember(references) { PlaceMatch.thresholdFor(references) }

    if (cameraOn && embedder == null) {
        Text(
            "The image model is missing from this build, so photos cannot be checked. Use a " +
                "barcode for now.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        return
    }

    when {
        capturing -> {
            Text(
                "Photo ${taken.size + 1} of ${PlaceMatch.REFERENCE_PHOTOS}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                PHOTO_HINTS[taken.size.coerceAtMost(PHOTO_HINTS.lastIndex)],
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            PlaceCamera(
                onFrame = { frame -> embedder?.embed(frame)?.let { latest = it } },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { capturing = false; taken = emptyList() },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("Cancel") }
                Button(
                    enabled = latest != null,
                    onClick = {
                        val shot = latest ?: return@Button
                        val next = taken + shot
                        if (next.size >= PlaceMatch.REFERENCE_PHOTOS) {
                            onRegistered(PlaceMatch.encode(next), PlaceMatch.thresholdFor(next))
                            taken = emptyList()
                            capturing = false
                            trying = true
                        } else {
                            taken = next
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("Take photo") }
            }
        }

        trying -> {
            val passes = score >= bar
            Text(
                if (passes) "Match - this would switch the alarm off" else "Not a match",
                style = MaterialTheme.typography.titleMedium,
                color = if (passes) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Point it at the place: it should match. Then at the bed: it should not. If the bed " +
                    "matches too, retake the photos somewhere with more to see.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            PlaceCamera(
                onFrame = { frame ->
                    embedder?.embed(frame)?.let { score = PlaceMatch.bestMatch(it, references) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (score / bar).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { trying = false; score = 0f },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("Done") }
        }

        registered.isNotBlank() -> {
            Text(
                "Registered: ${references.size} photos of your place.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        if (hasCameraPermission(context)) trying = true else openCamera()
                    },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("Try it") }
                OutlinedButton(
                    onClick = openCamera,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("Retake") }
            }
        }

        else -> {
            Text(
                "Pick a spot with things in it that do not move - the sink and mirror, the toilet " +
                    "and tiles. A blank wall is too easy to mistake for another blank wall. Only " +
                    "the photos' fingerprints are kept, never the photos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = openCamera,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("Take 3 photos") }
        }
    }

    if (refused) CameraRefused()
}
