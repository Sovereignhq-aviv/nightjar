package org.sovereignhq.sleepwave.ui

import android.content.Context
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.sovereignhq.sleepwave.data.ALL_TAGS
import org.sovereignhq.sleepwave.data.SleepSession
import org.sovereignhq.sleepwave.service.AlarmScheduler
import org.sovereignhq.sleepwave.ui.components.AlarmDial
import org.sovereignhq.sleepwave.ui.components.EmptyState
import org.sovereignhq.sleepwave.ui.components.Hypnogram
import org.sovereignhq.sleepwave.ui.components.HypnogramAxis
import org.sovereignhq.sleepwave.ui.components.NightCard
import org.sovereignhq.sleepwave.ui.components.ScoreRing
import org.sovereignhq.sleepwave.ui.components.SectionLabel
import org.sovereignhq.sleepwave.ui.components.StageLegend
import org.sovereignhq.sleepwave.ui.components.StatTile
import org.sovereignhq.sleepwave.ui.theme.DataColors
import kotlin.math.roundToInt

/**
 * Tonight's alarm above last night's sleep. One screen because they are the same question asked
 * twice - "when should I be woken" and "how did that go" - and splitting them across two
 * destinations would mean two taps to answer either.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepScreen(
    vm: SleepViewModel,
    onStartNight: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val session = vm.selectedSession
    val settings = vm.settings

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Text("Tonight", style = MaterialTheme.typography.headlineMedium)

        NightCard {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SectionLabel("Wake up by")
                Switch(
                    checked = settings.alarmEnabled,
                    onCheckedChange = { on -> vm.updateSettings { copy(alarmEnabled = on) } }
                )
            }

            Spacer(Modifier.height(10.dp))

            AlarmDial(
                hour = settings.alarmHour,
                minute = settings.alarmMinute,
                windowMinutes = settings.windowMinutes,
                enabled = settings.alarmEnabled,
                onChange = { h, m ->
                    vm.updateSettings { copy(alarmHour = h, alarmMinute = m) }
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = if (settings.windowMinutes > 0) {
                    "The band on the dial is when it might wake you - it picks the lightest moment " +
                        "inside it."
                } else {
                    "Rings exactly on time. No smart window."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(18.dp))
            SectionLabel("Smart window")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 15, 30, 45).forEach { minutes ->
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (settings.windowMinutes == minutes) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                            .clickable { vm.updateSettings { copy(windowMinutes = minutes) } }
                            .padding(vertical = 15.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (minutes == 0) "Off" else "${minutes}m",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (settings.windowMinutes == minutes) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }

        Button(
            onClick = onStartNight,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text("Start the night", style = MaterialTheme.typography.titleLarge)
        }

        Text(
            text = "Phone on the nightstand or the edge of the mattress, plugged in, screen down. " +
                "Leave the microphone unblocked - under a pillow it hears almost nothing.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ReliabilityWarnings(context)

        if (session == null) {
            EmptyState(
                title = "No nights yet",
                body = "Once you have tracked a night, the graph of how you slept and the score " +
                    "for it show up here."
            )
            Spacer(Modifier.height(24.dp))
            return@Column
        }

        LastNight(vm, session)
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LastNight(vm: SleepViewModel, session: SleepSession) {
    val stats = vm.stats(session)
    var noteDraft by remember(session.id) { mutableStateOf(session.note) }
    var confirmingDelete by remember { mutableStateOf(false) }

    Text(formatDay(session.startedAtMs), style = MaterialTheme.typography.headlineMedium)

    NightCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            ScoreRing(stats.score)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("asleep", formatDuration(stats.asleepMinutes))
                StatTile("deep sleep", formatDuration(stats.deepMinutes), DataColors.stageDeep)
                StatTile("fell asleep in", formatDuration(stats.sleepOnsetMinutes))
            }
        }
        val wokeAt = session.wokeAtMs
        if (wokeAt != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (session.wokeSmart) {
                    val early = ((session.alarmTargetMs - wokeAt) / 60_000L).coerceAtLeast(0L)
                    "Woken at ${formatClock(wokeAt)} in light sleep, $early min before your alarm."
                } else {
                    "Woken at ${formatClock(wokeAt)} on the deadline - no light-sleep moment came " +
                        "up inside the window."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (session.wokeSmart) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }

    SectionLabel("The night")
    NightCard {
        Hypnogram(samples = session.samples)
        Spacer(Modifier.height(6.dp))
        HypnogramAxis(
            startLabel = formatClock(session.startedAtMs),
            endLabel = formatClock(session.endedAtMs)
        )
        Spacer(Modifier.height(14.dp))
        StageLegend(showRem = stats.remMinutes > 0)
    }

    SectionLabel("Breakdown")
    NightCard {
        Row(Modifier.fillMaxWidth()) {
            StatTile("light sleep", formatDuration(stats.lightMinutes), DataColors.stageLight, Modifier.weight(1f))
            StatTile("awake", formatDuration(stats.awakeMinutes), DataColors.stageAwake, Modifier.weight(1f))
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth()) {
            StatTile("wake-ups", "${stats.awakenings}", modifier = Modifier.weight(1f))
            StatTile("of that, asleep", "${stats.efficiencyPct}%", modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth()) {
            StatTile(
                label = if (stats.remMinutes > 0) "REM (estimated)" else "REM",
                value = if (stats.remMinutes > 0) formatDuration(stats.remMinutes) else "not detected",
                tint = if (stats.remMinutes > 0) DataColors.stageRem else null,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "breathing",
                value = if (stats.breathRate > 0f) "${stats.breathRate.roundToInt()}/min" else "-",
                modifier = Modifier.weight(1f)
            )
        }
        if (stats.snoreMinutes > 0) {
            Spacer(Modifier.height(18.dp))
            StatTile("snoring", formatDuration(stats.snoreMinutes), DataColors.snore)
        }
        if (session.clips.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            StatTile("recordings", "${session.clips.size}", MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = if (stats.remMinutes > 0) {
                "REM is worked out from how unsteady your breathing was while you lay still. It is " +
                    "the least certain figure here - treat it as a hint, not a measurement."
            } else {
                "REM needs a clear breathing signal for most of the night. Move the phone closer, " +
                    "or onto the mattress, and it may pick it up."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    SectionLabel("How do you feel?")
    NightCard {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            (1..5).forEach { star ->
                IconButton(
                    onClick = { vm.setRating(session, star) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (star <= session.ratingStars) {
                            Icons.Rounded.Star
                        } else {
                            Icons.Rounded.StarBorder
                        },
                        contentDescription = "Rate $star out of 5",
                        tint = if (star <= session.ratingStars) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionLabel("Yesterday I had")
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ALL_TAGS.forEach { tag ->
                val on = tag in session.tags
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (on) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { vm.toggleTag(session, tag) }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (on) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = noteDraft,
            onValueChange = { noteDraft = it },
            label = { Text("Note") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { vm.setNote(session, noteDraft) }) { Text("Save note") }
    }

    TextButton(onClick = { confirmingDelete = true }) {
        Text("Delete this night", color = MaterialTheme.colorScheme.error)
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete this night?") },
            text = {
                Text(
                    "The graph, the score and every recording from it go for good.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    vm.deleteSession(session)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Keep") }
            }
        )
    }
}

/**
 * The two settings that quietly break overnight tracking on most Android phones. Surfaced here
 * rather than buried in Settings, because the failure mode is a missed alarm.
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
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "Fix this before you rely on the alarm",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.tertiary
        )
        if (!batteryUnrestricted) {
            Text(
                "Battery saving can shut SleepWave down mid-night. Settings has a one-tap fix.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!exactAlarms) {
            Text(
                "Exact alarms are switched off for this app, so the alarm may fire late.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
