package org.sovereignhq.nightjar.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.sovereignhq.nightjar.ui.components.HairLine
import org.sovereignhq.nightjar.ui.components.MicCheckCard
import org.sovereignhq.nightjar.ui.components.NightCard

/** In the order a missed alarm would punish skipping them. */
private enum class SetupStep { PERMISSIONS, RELIABILITY, MIC, SPOT }

/**
 * First open only. Everything here also lives elsewhere - permissions and reliability in Settings,
 * the wake-up spot on the Sleep tab - so skipping loses nothing. What it saves a newcomer is
 * finding those places by trial and error, usually after the morning the alarm did not ring.
 */
@Composable
fun SetupScreen(vm: SleepViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(SetupStep.PERMISSIONS) }

    // Most fixes happen in Android's own settings screens. Coming back to the app is the only
    // signal that something may have changed, so every resume re-reads the checks.
    var resumes by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumes++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = step != SetupStep.PERMISSIONS) {
        step = SetupStep.entries[step.ordinal - 1]
    }

    fun next() {
        if (step == SetupStep.entries.last()) onDone()
        else step = SetupStep.entries[step.ordinal + 1]
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Setup ${step.ordinal + 1} of ${SetupStep.entries.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onDone) { Text("Skip setup") }
        }
        LinearProgressIndicator(
            progress = { (step.ordinal + 1f) / SetupStep.entries.size },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )

        when (step) {
            SetupStep.PERMISSIONS -> PermissionsStep(onNext = ::next)
            SetupStep.RELIABILITY -> ReliabilityStep(resumes = resumes, onNext = ::next)
            SetupStep.MIC -> MicStep(context = context, resumes = resumes, onNext = ::next)
            SetupStep.SPOT -> SpotStep(vm = vm, onFinish = ::next)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StepTitle(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium)
    Text(
        body,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp)
    ) { Text(label, style = MaterialTheme.typography.titleMedium) }
}

@Composable
private fun PermissionsStep(onNext: () -> Unit) {
    val context = LocalContext.current
    var refused by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) onNext() else refused = true
    }

    StepTitle(
        "Welcome to Nightjar",
        "It listens to your bedroom overnight, keeps a short clip of anything that stands out, and " +
            "wakes you while you are already stirring. Audio is analysed on this phone and never " +
            "uploaded."
    )
    NightCard {
        Text(
            "Two permissions first: the microphone, to hear the night, and notifications, so the " +
                "alarm can reach you while the phone is locked.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (micGranted(context)) {
        PrimaryButton("Next", onNext)
    } else {
        PrimaryButton("Allow microphone and notifications") {
            launcher.launch(requiredPermissions())
        }
    }
    if (refused) {
        Text(
            "Without the microphone Nightjar cannot track a night or time the smart alarm. You can " +
                "allow it later in Settings, under App permissions.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.tertiary
        )
        TextButton(onClick = onNext) { Text("Continue without it") }
    }
}

@Composable
private fun ReliabilityStep(resumes: Int, onNext: () -> Unit) {
    val context = LocalContext.current
    // Keyed on resumes: each value is read again when the user comes back from Android's settings.
    val battery = remember(resumes) { Reliability.batteryUnrestricted(context) }
    val exact = remember(resumes) { Reliability.exactAlarmsAllowed(context) }
    val wakeScreen = remember(resumes) { Reliability.wakeScreenAllowed(context) }

    StepTitle(
        "Keep the alarm alive overnight",
        "Android quietly puts apps to sleep to save battery, and an app that is asleep cannot ring. " +
            "Tap each one marked Fix: Android opens its own screen, you allow it, then come back here."
    )
    NightCard {
        StatusRow(
            title = "Battery use unrestricted",
            ok = battery,
            detail = if (battery) "Nightjar can run all night." else "Otherwise Android may stop it mid-night.",
            onClick = { Reliability.openBatterySettings(context) }
        )
        Spacer(Modifier.height(14.dp))
        HairLine()
        Spacer(Modifier.height(14.dp))
        StatusRow(
            title = "Exact alarms allowed",
            ok = exact,
            detail = if (exact) "The alarm fires on time." else "Otherwise the alarm can be late.",
            onClick = { Reliability.openExactAlarmSettings(context) }
        )
        Spacer(Modifier.height(14.dp))
        HairLine()
        Spacer(Modifier.height(14.dp))
        StatusRow(
            title = "Wake-up screen allowed",
            ok = wakeScreen,
            detail = if (wakeScreen) {
                "The alarm can show its own screen over the lock screen."
            } else {
                "Otherwise it can only be stopped from the notification shade."
            },
            onClick = { Reliability.openWakeScreenSettings(context) }
        )
    }
    PrimaryButton(if (battery && exact && wakeScreen) "Next" else "Next, I'll fix it later", onNext)
}

@Composable
private fun MicStep(context: Context, resumes: Int, onNext: () -> Unit) {
    val granted = remember(resumes) { micGranted(context) }
    StepTitle(
        "Check it can hear the room",
        "Put the phone where it will sleep tonight: on the nightstand or the edge of the mattress, " +
            "plugged in, screen down. Not under a pillow - it hears almost nothing there. Then run the check."
    )
    NightCard {
        if (granted) {
            MicCheckCard()
        } else {
            Text(
                "The microphone is not allowed yet, so there is nothing to check. Go back a step, " +
                    "or allow it later in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
    PrimaryButton("Next", onNext)
}

@Composable
private fun SpotStep(vm: SleepViewModel, onFinish: () -> Unit) {
    val settings = vm.settings
    StepTitle(
        "Last step: your wake-up spot",
        "This is what makes Nightjar different. Pick a spot away from the bed and photograph it now. " +
            "You can change it any time on the Sleep tab."
    )
    NightCard {
        WakeMissionSetting(
            wakeCode = settings.wakeCode,
            wakePhotoRefs = settings.wakePhotoRefs,
            wakeDance = settings.wakeDance,
            onCode = { code -> vm.updateSettings { copy(wakeCode = code, wakePhotoRefs = "", wakeDance = false) } },
            onPhoto = { refs, threshold ->
                vm.updateSettings {
                    copy(wakePhotoRefs = refs, wakePhotoThreshold = threshold, wakeCode = "", wakeDance = false)
                }
            },
            onDance = { vm.updateSettings { copy(wakeDance = true, wakeCode = "", wakePhotoRefs = "") } },
            onOff = { vm.updateSettings { copy(wakeCode = "", wakePhotoRefs = "", wakeDance = false) } }
        )
    }
    val registered = settings.wakePhotoRefs.isNotBlank() || settings.wakeCode.isNotBlank() ||
        settings.wakeDance
    PrimaryButton(if (registered) "Finish" else "Finish without a spot", onFinish)
}

private fun micGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

/** The runtime permissions a night needs. Notifications only became one on Android 13. */
fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf(Manifest.permission.RECORD_AUDIO)
    }
