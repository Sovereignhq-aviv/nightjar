package org.sovereignhq.nightjar.ui

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.OpenableColumns
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.sovereignhq.nightjar.audio.AudioSetLabels
import org.sovereignhq.nightjar.data.EventKind
import org.sovereignhq.nightjar.data.Sensitivity
import org.sovereignhq.nightjar.alarm.PuzzleGenerator
import org.sovereignhq.nightjar.service.AlarmScheduler
import org.sovereignhq.nightjar.service.Notifications
import org.sovereignhq.nightjar.service.SleepService
import org.sovereignhq.nightjar.ui.components.HairLine
import org.sovereignhq.nightjar.ui.components.UpdateCard
import org.sovereignhq.nightjar.ui.components.MicCheckCard
import org.sovereignhq.nightjar.ui.components.LegendDot
import org.sovereignhq.nightjar.ui.components.NightCard
import org.sovereignhq.nightjar.ui.components.SectionLabel
import org.sovereignhq.nightjar.ui.theme.DataColors
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: SleepViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings = vm.settings

    val ringtonePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        @Suppress("DEPRECATION")
        val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        val label = uri?.let {
            runCatching { RingtoneManager.getRingtone(context, it)?.getTitle(context) }.getOrNull()
        }.orEmpty()
        vm.updateSettings { copy(alarmSoundUri = uri?.toString().orEmpty(), alarmSoundLabel = label) }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // The grant has to be persisted or the alarm cannot read the file after a reboot, which
            // would mean a silent alarm on exactly the morning nobody was expecting one.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val label = displayNameOf(context, uri)
            vm.updateSettings { copy(alarmSoundUri = uri.toString(), alarmSoundLabel = label) }
        }
    }

    val soundName = remember(settings.alarmSoundUri, settings.alarmSoundLabel) {
        when {
            settings.alarmSoundLabel.isNotBlank() -> settings.alarmSoundLabel
            settings.alarmSoundUri.isBlank() -> "Default alarm sound"
            else -> runCatching {
                RingtoneManager.getRingtone(context, Uri.parse(settings.alarmSoundUri))
                    ?.getTitle(context)
            }.getOrNull() ?: "Chosen sound"
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        SectionLabel("Updates")
        NightCard {
            UpdateCard(
                state = vm.updateState,
                installedVersion = vm.installedVersion,
                autoCheckEnabled = settings.autoUpdateCheck,
                onToggleAutoCheck = { on -> vm.updateSettings { copy(autoUpdateCheck = on) } },
                onCheck = { vm.checkForUpdate() },
                onDownload = { vm.downloadUpdate() },
                onInstall = {
                    val intent = vm.installIntent()
                    if (intent != null) runCatching { context.startActivity(intent) }
                },
                onDismiss = { vm.dismissUpdate() }
            )
        }

        // ---------------------------------------------------------- recordings
        SectionLabel("Recording")
        NightCard {
            val storageMb = remember { vm.clipBytes() / 1_000_000 }

            ToggleRow(
                title = "Record what it hears",
                subtitle = "Short clips only, saved when something stands out. Turn this off and " +
                    "the app still tracks sleep and rings the alarm.",
                checked = settings.recordSounds,
                onChange = { on -> vm.updateSettings { copy(recordSounds = on) } }
            )

            Spacer(Modifier.height(18.dp))
            HairLine()
            Spacer(Modifier.height(16.dp))

            SectionLabel("How much to catch")
            Spacer(Modifier.height(10.dp))
            Sensitivity.entries.forEach { option ->
                val selected = settings.sensitivity == option
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { vm.updateSettings { copy(sensitivity = option) } }
                        .padding(horizontal = 14.dp, vertical = 13.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            option.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "up to ${option.maxClipsPerNight}/night",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        option.blurb,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            SliderRow(
                title = "Keep recordings for",
                value = settings.clipRetentionDays.toFloat(),
                range = 1f..60f,
                steps = 0,
                display = "${settings.clipRetentionDays} days",
                subtitle = "Anything you star is kept forever. Everything else is deleted after " +
                    "this. Audio on this phone right now: $storageMb MB.",
                onChange = { v -> vm.updateSettings { copy(clipRetentionDays = v.roundToInt()) } }
            )

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { vm.cleanUpNow() },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("Clean up now") }
        }

        SectionLabel("Microphone check")
        NightCard { MicCheckCard() }

        if (settings.mutedLabels.isNotEmpty()) {
            SectionLabel("Sounds you have muted")
            NightCard {
                Text(
                    "Never recorded again. Everything else is unaffected, so this is not the same " +
                        "as turning the sensitivity down.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                settings.mutedLabels.sorted().forEach { label ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.unmuteDetail(label) }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, style = MaterialTheme.typography.titleMedium)
                        Text("Unmute", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        SectionLabel("What the labels mean")
        NightCard {
            EventKind.entries.forEach { kind ->
                Column(Modifier.padding(vertical = 7.dp)) {
                    LegendDot(DataColors.forEvent(kind), eventLabel(kind))
                    Text(
                        eventDescription(kind),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 17.dp)
                    )
                }
            }
        }

        SectionLabel("Teaching it")
        NightCard {
            val corrections = remember(vm.sessions) { vm.correctionCount() }
            Text(
                "Labels come from YAMNet, a model Google trained on two million sounds. It already " +
                    "knows ${AudioSetLabels.recognisedCount} of the noises this app cares about by " +
                    "name — including which ones are the air conditioning rather than you.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = when (corrections) {
                    0 -> "When it gets one wrong, tap the label in the player and pick the right " +
                        "one. Corrected clips are kept forever instead of being deleted after a " +
                        "week, because they are what a model tuned to your bedroom would learn from."
                    else -> "$corrections correction${if (corrections == 1) "" else "s"} saved so " +
                        "far. Around 50 per kind is enough to train a version tuned to your " +
                        "bedroom and the specific people in it."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (corrections > 0) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        // ---------------------------------------------------------------- alarm
        SectionLabel("Alarm")
        NightCard {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text("Alarm sound", style = MaterialTheme.typography.titleMedium)
                Text(
                    soundName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            // TYPE_ALL rather than TYPE_ALARM: on most phones the good sounds are
                            // filed as ringtones, and there is no reason to hide them.
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sound")
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                settings.alarmSoundUri.takeIf { it.isNotBlank() }
                                    ?.let { Uri.parse(it) }
                            )
                        }
                        runCatching { ringtonePicker.launch(intent) }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) { Text("Phone sounds") }

                OutlinedButton(
                    onClick = { runCatching { filePicker.launch(arrayOf("audio/*")) } },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) { Text("A song file") }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "A song file means any MP3 or M4A on the phone, so a downloaded track works as an " +
                    "alarm. Spotify is not wired up yet: it needs a one-time account authorisation, " +
                    "Spotify Premium, and their app installed. Until then, a downloaded file is the " +
                    "reliable route.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(14.dp))
            HairLine()
            Spacer(Modifier.height(14.dp))

            ToggleRow(
                title = "Vibrate",
                subtitle = "Gentle pulse alongside the sound",
                checked = settings.vibrate,
                onChange = { on -> vm.updateSettings { copy(vibrate = on) } }
            )

            Spacer(Modifier.height(18.dp))
            SliderRow(
                title = "Fade in over",
                value = settings.rampSeconds.toFloat(),
                range = 0f..120f,
                steps = 11,
                display = if (settings.rampSeconds == 0) "instantly" else "${settings.rampSeconds}s",
                subtitle = "Starts almost silent and climbs to full volume",
                onChange = { v -> vm.updateSettings { copy(rampSeconds = v.roundToInt()) } }
            )

            Spacer(Modifier.height(18.dp))
            SliderRow(
                title = "Snooze",
                value = settings.snoozeMinutes.toFloat(),
                range = 1f..20f,
                steps = 18,
                display = "${settings.snoozeMinutes} min",
                subtitle = null,
                onChange = { v -> vm.updateSettings { copy(snoozeMinutes = v.roundToInt()) } }
            )

            Spacer(Modifier.height(18.dp))
            HairLine()
            Spacer(Modifier.height(16.dp))

            SectionLabel("Sums before it switches off")
            Spacer(Modifier.height(6.dp))
            Text(
                "A puzzle you cannot solve with a thumb while the rest of you stays asleep. " +
                    "Snoozing never needs one, and there is always a button that silences the alarm " +
                    "immediately without switching it off, so you can get up without waking anyone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Off", 1 to "1 digit", 2 to "2 digits", 3 to "3 digits")
                    .forEach { (digits, label) ->
                        val selected = settings.puzzleDigits == digits
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { vm.updateSettings { copy(puzzleDigits = digits) } }
                                .padding(vertical = 15.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
            }

            if (settings.puzzleDigits > 0) {
                Spacer(Modifier.height(16.dp))
                SliderRow(
                    title = "How many",
                    value = settings.puzzleCount.toFloat(),
                    range = 1f..3f,
                    steps = 1,
                    display = PuzzleGenerator.describe(settings.puzzleDigits, settings.puzzleCount),
                    subtitle = null,
                    onChange = { v -> vm.updateSettings { copy(puzzleCount = v.roundToInt()) } }
                )
            }

            Spacer(Modifier.height(18.dp))
            OutlinedButton(
                onClick = { SleepService.send(context, SleepService.ACTION_FIRE_ALARM) },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("Test the alarm now") }
            Spacer(Modifier.height(6.dp))
            Text(
                "Worth doing once. It also tells you whether your alarm volume is turned up.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ---------------------------------------------------------------- sleep
        SectionLabel("Sleep tracking")
        NightCard {
            SliderRow(
                title = "Sleep goal",
                value = settings.sleepGoalMinutes.toFloat(),
                range = 300f..600f,
                steps = 19,
                display = formatDuration(settings.sleepGoalMinutes),
                subtitle = "Used for the quality score and the goal line on Trends",
                onChange = { v ->
                    vm.updateSettings { copy(sleepGoalMinutes = (v / 15f).roundToInt() * 15) }
                }
            )

            Spacer(Modifier.height(18.dp))
            HairLine()
            Spacer(Modifier.height(14.dp))

            ToggleRow(
                title = "Use the movement sensor",
                subtitle = "Adds the accelerometer to sound. Useful when the phone lies on the " +
                    "mattress; harmless on a nightstand.",
                checked = settings.motionSensing,
                onChange = { on -> vm.updateSettings { copy(motionSensing = on) } }
            )

            Spacer(Modifier.height(18.dp))
            SliderRow(
                title = "Keep nights for",
                value = settings.nightRetentionDays.toFloat(),
                range = 30f..730f,
                steps = 0,
                display = "${settings.nightRetentionDays} days",
                subtitle = "Graphs and scores only - a few hundred KB a year. Longer history makes " +
                    "Trends more useful. Nights holding starred audio are kept regardless.",
                onChange = { v -> vm.updateSettings { copy(nightRetentionDays = v.roundToInt()) } }
            )
        }

        // ---------------------------------------------------------- reliability
        SectionLabel("Keeping it reliable")
        NightCard {
            val powerManager = context.getSystemService(PowerManager::class.java)
            val unrestricted =
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true

            StatusRow(
                title = "Battery use unrestricted",
                ok = unrestricted,
                detail = if (unrestricted) {
                    "Android will leave Nightjar running overnight."
                } else {
                    "Android may shut Nightjar down mid-night. Tap to fix."
                },
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        )
                    }
                }
            )

            Spacer(Modifier.height(14.dp))
            HairLine()
            Spacer(Modifier.height(14.dp))

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

            Spacer(Modifier.height(14.dp))
            HairLine()
            Spacer(Modifier.height(14.dp))

            val canShowAlarmScreen = Notifications.canShowFullScreenAlarm(context)
            StatusRow(
                title = "Wake-up screen allowed",
                ok = canShowAlarmScreen,
                detail = if (canShowAlarmScreen) {
                    "The alarm can put its own screen in front of you."
                } else {
                    "Android is blocking the wake-up screen, so the alarm can only be stopped from " +
                        "the notification shade. Tap to allow it."
                },
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        runCatching {
                            context.startActivity(
                                Intent(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                                    .setData(Uri.parse("package:${context.packageName}"))
                            )
                        }
                    }
                }
            )

            Spacer(Modifier.height(14.dp))
            HairLine()
            Spacer(Modifier.height(14.dp))

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
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.fillMaxWidth(0.8f)) {
                    Text("App permissions", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Microphone and notifications live here if you ever need to change them.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("Open", color = MaterialTheme.colorScheme.primary)
            }
        }

        // --------------------------------------------------------------- honesty
        SectionLabel("How this works")
        NightCard {
            Text(
                "The microphone stays open all night, but audio is not written to disk as it goes " +
                    "- it sits in a fourteen-second loop in memory and is continuously overwritten. " +
                    "A clip is only saved when something crosses the threshold, and it reaches back " +
                    "three seconds so you hear the start of it rather than the aftermath.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Sleep stages come from how much movement and sound there is each minute, scaled " +
                    "against your own quietest and busiest levels for that night. It is a tuned " +
                    "estimate, not a medical measurement: it cannot see REM sleep, and it cannot " +
                    "tell your movement from someone else's in the same bed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "No account, no internet, no analytics. Recordings and graphs stay in this app's " +
                    "private storage on this phone until you share or delete them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(24.dp))
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
        Column(Modifier.fillMaxWidth(0.76f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                display,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            steps = steps
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusRow(title: String, ok: Boolean, detail: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !ok, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.fillMaxWidth(0.8f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = if (ok) "OK" else "Fix",
            style = MaterialTheme.typography.labelLarge,
            color = if (ok) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
        )
    }
}

/** The file name behind a content:// URI, so a chosen song shows its own name in Settings. */
private fun displayNameOf(context: Context, uri: Uri): String = runCatching {
    context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
}.getOrNull() ?: "Chosen audio file"
