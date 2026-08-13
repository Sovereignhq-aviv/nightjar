package org.sovereignhq.sleepwave.ui

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.sovereignhq.sleepwave.service.AlarmScheduler
import org.sovereignhq.sleepwave.service.SleepService
import org.sovereignhq.sleepwave.ui.components.HairLine
import org.sovereignhq.sleepwave.ui.components.NightCard
import org.sovereignhq.sleepwave.ui.components.SectionLabel
import org.sovereignhq.sleepwave.ui.theme.Amber
import org.sovereignhq.sleepwave.ui.theme.TextMuted
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: SleepViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val soundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        @Suppress("DEPRECATION")
        val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        vm.setAlarmSoundUri(uri?.toString() ?: "")
    }

    val soundName = remember(vm.alarmSoundUri) {
        val uri = vm.alarmSoundUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }
            .getOrNull() ?: "Default alarm sound"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        SectionLabel("Alarm")
        NightCard {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sound")
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                vm.alarmSoundUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
                            )
                        }
                        runCatching { soundPicker.launch(intent) }
                    }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Alarm sound", style = MaterialTheme.typography.titleMedium)
                    Text(soundName, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                }
                Text("Change", color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(12.dp))
            HairLine()
            Spacer(Modifier.height(12.dp))

            ToggleRow(
                title = "Vibrate",
                subtitle = "Gentle pulse alongside the sound",
                checked = vm.vibrate,
                onChange = { vm.setVibrate(it) }
            )

            Spacer(Modifier.height(16.dp))
            SliderRow(
                title = "Fade in over",
                value = vm.rampSeconds.toFloat(),
                range = 0f..120f,
                steps = 11,
                display = if (vm.rampSeconds == 0) "instantly" else "${vm.rampSeconds}s",
                subtitle = "Starts almost silent and climbs to full volume",
                onChange = { vm.setRampSeconds(it.roundToInt()) }
            )

            Spacer(Modifier.height(16.dp))
            SliderRow(
                title = "Snooze",
                value = vm.snoozeMinutes.toFloat(),
                range = 1f..20f,
                steps = 18,
                display = "${vm.snoozeMinutes} min",
                subtitle = null,
                onChange = { vm.setSnoozeMinutes(it.roundToInt()) }
            )

            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { SleepService.send(context, SleepService.ACTION_FIRE_ALARM) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Test the alarm now") }
            Spacer(Modifier.height(6.dp))
            Text(
                "Worth doing once. It also tells you whether your alarm volume is turned up.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }

        SectionLabel("Sleep")
        NightCard {
            SliderRow(
                title = "Sleep goal",
                value = vm.sleepGoalMinutes.toFloat(),
                range = 300f..600f,
                steps = 19,
                display = formatDuration(vm.sleepGoalMinutes),
                subtitle = "Used for the quality score and the goal line on trends",
                onChange = { vm.setSleepGoalMinutes((it / 15f).roundToInt() * 15) }
            )

            Spacer(Modifier.height(16.dp))
            HairLine()
            Spacer(Modifier.height(12.dp))

            ToggleRow(
                title = "Use the movement sensor",
                subtitle = "Adds the accelerometer to sound. Useful when the phone lies on the " +
                    "mattress; harmless on a nightstand.",
                checked = vm.motionSensing,
                onChange = { vm.setMotionSensing(it) }
            )
        }

        SectionLabel("Recordings")
        NightCard {
            // Listing the clips folder touches the disk, so it is measured once per visit.
            val storageMb = remember { vm.clipBytes() / 1_000_000 }

            ToggleRow(
                title = "Record snoring and noise",
                subtitle = "Short clips only, saved when snoring or a loud noise is detected. " +
                    "Nothing else is written to disk.",
                checked = vm.recordSnoring,
                onChange = { vm.setRecordSnoring(it) }
            )

            Spacer(Modifier.height(16.dp))
            SliderRow(
                title = "Delete nights after",
                value = vm.autoDeleteDays.toFloat(),
                range = 7f..180f,
                steps = 0,
                display = "${vm.autoDeleteDays} days",
                subtitle = "Audio uses about 0.6 MB per clip. Currently $storageMb MB on this phone.",
                onChange = { vm.setAutoDeleteDays(it.roundToInt()) }
            )

            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { vm.pruneNow() }, modifier = Modifier.fillMaxWidth()) {
                Text("Clean up old nights now")
            }
        }

        SectionLabel("Keeping it reliable")
        NightCard {
            val powerManager = context.getSystemService(PowerManager::class.java)
            val unrestricted =
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true

            StatusRow(
                title = "Battery use unrestricted",
                ok = unrestricted,
                detail = if (unrestricted) {
                    "Android will leave SleepWave running overnight."
                } else {
                    "Android may shut SleepWave down mid-night. Tap to fix."
                },
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        )
                    }
                }
            )

            Spacer(Modifier.height(12.dp))
            HairLine()
            Spacer(Modifier.height(12.dp))

            val exact = AlarmScheduler.canScheduleExact(context)
            StatusRow(
                title = "Exact alarms allowed",
                ok = exact,
                detail = if (exact) {
                    "The alarm will fire at the exact time."
                } else {
                    "Without this the alarm can be delayed. Tap to fix."
                },
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        runCatching {
                            context.startActivity(
                                Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                    .setData(Uri.parse("package:${context.packageName}"))
                            )
                        }
                    }
                }
            )

            Spacer(Modifier.height(12.dp))
            HairLine()
            Spacer(Modifier.height(12.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        runCatching {
                            context.startActivity(
                                Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(Uri.parse("package:${context.packageName}"))
                            )
                        }
                    }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.fillMaxWidth(0.8f)) {
                    Text("App permissions", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Microphone and notifications live here if you ever need to change them.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
                Text("Open", color = MaterialTheme.colorScheme.primary)
            }
        }

        SectionLabel("How this works")
        NightCard {
            Text(
                "SleepWave keeps the microphone open all night and measures how much movement " +
                    "and sound there is each minute. Still minutes are scored as deep sleep, " +
                    "restless ones as light sleep, and clearly active ones as awake. Everything " +
                    "is scaled against your own quietest and busiest levels for that night, so " +
                    "a noisy street or a quiet cottage make no difference.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Worth knowing: this is a well-tuned estimate, not a medical measurement. It " +
                    "cannot see REM sleep, which shows up inside light sleep, and it cannot tell " +
                    "your movement from a partner's in the same bed. The smart alarm only needs " +
                    "to know whether you are restless right now, which is the part it does well.",
                style = MaterialTheme.typography.bodyMedium,
                color = Amber.copy(alpha = 0.85f)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "No account, no internet, no analytics. Recordings and graphs stay in this app's " +
                    "private storage on this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.fillMaxWidth(0.78f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    display: String,
    subtitle: String?,
    onChange: (Float) -> Unit
) {
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(display, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            steps = steps
        )
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
    }
}

@Composable
private fun StatusRow(title: String, ok: Boolean, detail: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !ok, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.fillMaxWidth(0.82f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
        Text(
            text = if (ok) "OK" else "Fix",
            style = MaterialTheme.typography.labelLarge,
            color = if (ok) MaterialTheme.colorScheme.secondary else Amber
        )
    }
}
