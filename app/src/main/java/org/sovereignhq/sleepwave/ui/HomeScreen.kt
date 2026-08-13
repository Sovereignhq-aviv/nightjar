package org.sovereignhq.sleepwave.ui

import android.app.TimePickerDialog
import android.content.Context
import android.os.PowerManager
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.sovereignhq.sleepwave.data.SleepSession
import org.sovereignhq.sleepwave.service.AlarmScheduler
import org.sovereignhq.sleepwave.ui.components.NightCard
import org.sovereignhq.sleepwave.ui.components.SectionLabel
import org.sovereignhq.sleepwave.ui.theme.Amber
import org.sovereignhq.sleepwave.ui.theme.Indigo
import org.sovereignhq.sleepwave.ui.theme.Mint
import org.sovereignhq.sleepwave.ui.theme.NightSurfaceHigh
import org.sovereignhq.sleepwave.ui.theme.TextMuted

@Composable
fun HomeScreen(
    vm: SleepViewModel,
    onStartNight: () -> Unit,
    onOpenSession: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        Text("Tonight", style = MaterialTheme.typography.headlineMedium)

        NightCard {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SectionLabel("Wake up by")
                Switch(
                    checked = vm.alarmEnabled,
                    onCheckedChange = { vm.setAlarmEnabled(it) }
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = formatHourMinute(vm.alarmHour, vm.alarmMinute),
                style = MaterialTheme.typography.displayLarge,
                color = if (vm.alarmEnabled) MaterialTheme.colorScheme.onSurface else TextMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        TimePickerDialog(
                            context,
                            { _, hour, minute -> vm.setAlarmTime(hour, minute) },
                            vm.alarmHour,
                            vm.alarmMinute,
                            true
                        ).show()
                    }
                    .padding(horizontal = 4.dp)
            )

            Text(
                text = if (vm.windowMinutes > 0) {
                    "Wakes you in light sleep, up to ${vm.windowMinutes} minutes early"
                } else {
                    "Rings exactly on time - no smart window"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )

            Spacer(Modifier.height(16.dp))
            SectionLabel("Smart window")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 15, 30, 45).forEach { minutes ->
                    WindowChip(
                        label = if (minutes == 0) "Off" else "${minutes}m",
                        selected = vm.windowMinutes == minutes,
                        onClick = { vm.setWindowMinutes(minutes) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Button(
            onClick = onStartNight,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Indigo)
        ) {
            Text(
                "Start the night",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }

        Text(
            text = "Put the phone on the nightstand or the edge of the mattress, screen down, " +
                "plugged in. It listens for movement and breathing - it does not need to touch you.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )

        ReliabilityWarnings(context)

        val last = vm.sessions.firstOrNull()
        if (last != null) {
            SectionLabel("Last night")
            LastNightCard(vm, last) { onOpenSession(last.id) }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun WindowChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Indigo else NightSurfaceHigh)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else TextMuted
        )
    }
}

@Composable
private fun LastNightCard(vm: SleepViewModel, session: SleepSession, onClick: () -> Unit) {
    val stats = vm.stats(session)
    NightCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(formatDay(session.startedAtMs), style = MaterialTheme.typography.titleMedium)
                Text(
                    "${formatClock(session.startedAtMs)} - ${formatClock(session.endedAtMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${stats.score}%",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Mint
                )
                Text(
                    formatDuration(stats.asleepMinutes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        }
    }
}

/**
 * The two settings that quietly break overnight tracking on most Android phones. Worth surfacing
 * on the home screen rather than burying in settings, because the failure mode is a missed alarm.
 */
@Composable
private fun ReliabilityWarnings(context: Context) {
    val powerManager = context.getSystemService(PowerManager::class.java)
    val batteryUnrestricted = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    val exactAlarms = AlarmScheduler.canScheduleExact(context)

    if (batteryUnrestricted && exactAlarms) return

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NightSurfaceHigh)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "Fix this before you rely on the alarm",
            style = MaterialTheme.typography.titleMedium,
            color = Amber,
            fontWeight = FontWeight.SemiBold
        )
        if (!batteryUnrestricted) {
            Text(
                "Battery saving can shut SleepWave down mid-night. Open Settings and set battery " +
                    "use to Unrestricted.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }
        if (!exactAlarms) {
            Text(
                "Exact alarms are switched off for this app, so the alarm may fire late. Open " +
                    "Settings and allow alarms and reminders.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }
    }
}
