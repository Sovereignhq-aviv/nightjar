package org.sovereignhq.sleepwave.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import org.sovereignhq.sleepwave.data.ALL_TAGS
import org.sovereignhq.sleepwave.data.SleepSession
import org.sovereignhq.sleepwave.ui.components.Hypnogram
import org.sovereignhq.sleepwave.ui.components.HypnogramAxis
import org.sovereignhq.sleepwave.ui.components.NightCard
import org.sovereignhq.sleepwave.ui.components.ScoreRing
import org.sovereignhq.sleepwave.ui.components.SectionLabel
import org.sovereignhq.sleepwave.ui.components.StageLegend
import org.sovereignhq.sleepwave.ui.components.StatTile
import org.sovereignhq.sleepwave.ui.theme.Amber
import org.sovereignhq.sleepwave.ui.theme.Indigo
import org.sovereignhq.sleepwave.ui.theme.Mint
import org.sovereignhq.sleepwave.ui.theme.NightSurfaceHigh
import org.sovereignhq.sleepwave.ui.theme.StageAwake
import org.sovereignhq.sleepwave.ui.theme.StageDeep
import org.sovereignhq.sleepwave.ui.theme.StageLight
import org.sovereignhq.sleepwave.ui.theme.TextMuted

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MorningScreen(
    vm: SleepViewModel,
    session: SleepSession,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stats = vm.stats(session)
    var noteDraft by remember(session.id) { mutableStateOf(session.note) }
    var confirmingDelete by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = TextMuted)
            }
            Column {
                Text(formatDay(session.startedAtMs), style = MaterialTheme.typography.titleLarge)
                Text(
                    "${formatClock(session.startedAtMs)} - ${formatClock(session.endedAtMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        }

        NightCard {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                ScoreRing(stats.score)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("asleep", formatDuration(stats.asleepMinutes))
                    StatTile("deep sleep", formatDuration(stats.deepMinutes), StageDeep)
                    StatTile("fell asleep in", formatDuration(stats.sleepOnsetMinutes))
                }
            }
            if (session.wokeAtMs != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (session.wokeSmart) {
                        "Woken at ${formatClock(session.wokeAtMs)} in light sleep, " +
                            "${((session.alarmTargetMs - session.wokeAtMs) / 60_000L)
                                .coerceAtLeast(0L)} min before your alarm."
                    } else {
                        "Woken at ${formatClock(session.wokeAtMs)} on the deadline - " +
                            "no light-sleep moment came up in the window."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (session.wokeSmart) Mint else TextMuted
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
            StageLegend()
        }

        SectionLabel("Breakdown")
        NightCard {
            Row(Modifier.fillMaxWidth()) {
                StatTile("light sleep", formatDuration(stats.lightMinutes), StageLight, Modifier.weight(1f))
                StatTile("awake", formatDuration(stats.awakeMinutes), StageAwake, Modifier.weight(1f))
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth()) {
                StatTile("wake-ups", "${stats.awakenings}", modifier = Modifier.weight(1f))
                StatTile("time in bed asleep", "${stats.efficiencyPct}%", modifier = Modifier.weight(1f))
            }
            if (stats.snoreMinutes > 0) {
                Spacer(Modifier.height(18.dp))
                StatTile("snoring", formatDuration(stats.snoreMinutes), Amber)
            }
        }

        if (session.clips.isNotEmpty()) {
            SectionLabel("Recordings (${session.clips.size})")
            NightCard(padding = 12) {
                session.clips.sortedBy { it.startedAtMs }.forEach { clip ->
                    val playing = vm.playingClip == clip.fileName
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { vm.playClip(clip.fileName) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (playing) Indigo else NightSurfaceHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                contentDescription = if (playing) "Stop" else "Play",
                                tint = if (playing) MaterialTheme.colorScheme.onPrimary else Indigo
                            )
                        }
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = if (clip.kind == "SNORE") "Snoring" else "Noise",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (clip.kind == "SNORE") Amber else TextMuted
                            )
                            Text(
                                text = "${formatClock(clip.startedAtMs)}  ·  " +
                                    "${clip.durationMs / 1000}s  ·  " +
                                    "${clip.peakDb.toInt()} dB above the room",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }

        SectionLabel("How do you feel?")
        NightCard {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..5).forEach { star ->
                    IconButton(onClick = { vm.setRating(session, star) }) {
                        Icon(
                            imageVector = if (star <= session.ratingStars) {
                                Icons.Rounded.Star
                            } else {
                                Icons.Rounded.StarBorder
                            },
                            contentDescription = "$star stars",
                            tint = if (star <= session.ratingStars) Amber else TextMuted
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
                            .background(if (on) Indigo else NightSurfaceHigh)
                            .clickable { vm.toggleTag(session, tag) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (on) MaterialTheme.colorScheme.onPrimary else TextMuted
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
            TextButton(onClick = { vm.setNote(session, noteDraft) }) {
                Text("Save note")
            }
        }

        TextButton(onClick = { confirmingDelete = true }) {
            Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text("  Delete this night", color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(32.dp))
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete this night?") },
            text = { Text("The graph and any recordings will be removed for good.", color = TextMuted) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    vm.delete(session)
                    onDeleted()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Keep") }
            }
        )
    }
}
