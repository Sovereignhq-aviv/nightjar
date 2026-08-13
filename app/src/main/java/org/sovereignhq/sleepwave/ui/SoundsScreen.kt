package org.sovereignhq.sleepwave.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.sovereignhq.sleepwave.data.EventKind
import org.sovereignhq.sleepwave.data.SleepSession
import org.sovereignhq.sleepwave.data.SoundClip
import org.sovereignhq.sleepwave.ui.components.EmptyState
import org.sovereignhq.sleepwave.ui.components.LoudnessBar
import org.sovereignhq.sleepwave.ui.components.Waveform
import org.sovereignhq.sleepwave.ui.theme.DataColors

/**
 * The main screen: everything the phone heard last night, in a list you can get through fast.
 *
 * Rows are dense and flat rather than carded. A hundred recordings in a hundred cards is a hundred
 * borders to read past; a list with a fixed time column on the left can be skimmed, which is the
 * only thing that matters when the interesting item is somewhere around 3am.
 */
@Composable
fun SoundsScreen(
    vm: SleepViewModel,
    tracking: Boolean,
    onStartNight: () -> Unit,
    modifier: Modifier = Modifier
) {
    val session = vm.selectedSession
    val clips = vm.visibleClips(session)
    val counts = vm.countsByKind(session)
    var pendingDelete by remember { mutableStateOf<SoundClip?>(null) }

    Column(modifier.fillMaxWidth()) {
        NightHeader(vm, session, tracking, onStartNight)

        if (session == null) {
            Box(Modifier.padding(20.dp)) {
                EmptyState(
                    title = "Nothing recorded yet",
                    body = "Start a night and SleepWave listens. Anything that stands out from the " +
                        "room's own quiet gets a short clip you can play back in the morning: " +
                        "snoring, talking, and every other noise a person makes asleep."
                )
            }
            return@Column
        }

        if (session.clips.isNotEmpty()) {
            FilterRow(vm, counts)
            SortRow(vm, clips.size)
        }

        if (clips.isEmpty()) {
            Box(Modifier.padding(20.dp)) {
                EmptyState(
                    title = if (session.clips.isEmpty()) "A silent night" else "Nothing matches",
                    body = if (session.clips.isEmpty()) {
                        "Not a single noise crossed the threshold. Either it was genuinely quiet, " +
                            "or the microphone was blocked - a phone face-down on a soft duvet " +
                            "hears very little. Settings can also make it more sensitive."
                    } else {
                        "No recordings of that kind on this night. Tap the filter again to clear it."
                    }
                )
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(clips, key = { it.fileName }) { clip ->
                ClipRow(
                    clip = clip,
                    isCurrent = vm.player.current?.fileName == clip.fileName,
                    progress = if (vm.player.current?.fileName == clip.fileName) {
                        vm.player.progress
                    } else {
                        -1f
                    },
                    onPlay = { vm.playClip(clip, clips) },
                    onLongPress = { pendingDelete = clip },
                    onStar = { vm.toggleStar(session, clip) }
                )
            }
        }
    }

    val doomed = pendingDelete
    if (doomed != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this recording?") },
            text = {
                Text(
                    "${eventLabel(doomed.eventKind)} at ${formatClock(doomed.startedAtMs)}. " +
                        "The audio goes for good.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    session?.let { vm.deleteClip(it, doomed) }
                    pendingDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Keep") }
            }
        )
    }
}

@Composable
private fun NightHeader(
    vm: SleepViewModel,
    session: SleepSession?,
    tracking: Boolean,
    onStartNight: () -> Unit
) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (session == null) "Sounds" else formatDay(session.startedAtMs),
                    style = MaterialTheme.typography.headlineMedium
                )
                if (session != null) {
                    val total = session.clips.size
                    val starred = session.clips.count { it.starred }
                    Text(
                        text = buildString {
                            append("$total recording${if (total == 1) "" else "s"}")
                            append("  ·  ${formatClock(session.startedAtMs)}-${formatClock(session.endedAtMs)}")
                            if (starred > 0) append("  ·  $starred saved")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!tracking) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable(onClick = onStartNight)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Bedtime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "  Start tonight",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        if (vm.sessions.size > 1) {
            Spacer(Modifier.height(14.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vm.sessions.take(14), key = { it.id }) { night ->
                    val selected = night.id == (vm.selectedSession?.id)
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { vm.selectSession(night.id) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = formatShortDay(night.startedAtMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun FilterRow(vm: SleepViewModel, counts: Map<EventKind, Int>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            FilterChip(
                label = "Saved",
                count = null,
                selected = vm.starredOnly,
                accent = DataColors.rumble,
                leadingStar = true,
                onClick = { vm.toggleStarredOnly() }
            )
        }
        items(EventKind.entries.filter { (counts[it] ?: 0) > 0 }) { kind ->
            FilterChip(
                label = eventLabelPlural(kind),
                count = counts[kind],
                selected = vm.kindFilter == kind,
                accent = DataColors.forEvent(kind),
                leadingStar = false,
                onClick = { vm.toggleKindFilter(kind) }
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    count: Int?,
    selected: Boolean,
    accent: Color,
    leadingStar: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) accent else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val ink = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant

        if (leadingStar) {
            Icon(
                Icons.Rounded.Star,
                contentDescription = null,
                tint = if (selected) ink else accent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
        } else if (!selected) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(Modifier.width(7.dp))
        }

        Text(
            text = if (count != null) "$label $count" else label,
            style = MaterialTheme.typography.labelMedium,
            color = ink
        )
    }
}

@Composable
private fun SortRow(vm: SleepViewModel, shown: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$shown shown",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            ClipSort.entries.forEach { option ->
                val selected = vm.sort == option
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { vm.chooseSort(option) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = if (option == ClipSort.TIME) "By time" else "Loudest",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClipRow(
    clip: SoundClip,
    isCurrent: Boolean,
    progress: Float,
    onPlay: () -> Unit,
    onLongPress: () -> Unit,
    onStar: () -> Unit
) {
    val accent = DataColors.forEvent(clip.eventKind)

    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (isCurrent) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.background
            )
            .combinedClickable(onClick = onPlay, onLongClick = onLongPress)
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(52.dp)) {
            Text(
                text = formatClock(clip.startedAtMs),
                style = MaterialTheme.typography.titleSmall,
                color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${clip.durationMs / 1000}s",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Text(
                    text = "  ${eventLabel(clip.eventKind)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                LoudnessBar(
                    peakAboveFloorDb = clip.peakDb,
                    color = accent,
                    modifier = Modifier
                        .width(52.dp)
                        .height(4.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Waveform(
                envelope = clip.envelope,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp),
                color = accent,
                progress = progress,
                bars = 40
            )
        }

        Spacer(Modifier.width(6.dp))

        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onStar),
            contentAlignment = Alignment.Center
        ) {
            if (clip.starred) {
                Icon(
                    Icons.Rounded.Star,
                    contentDescription = "Saved. Tap to unsave.",
                    tint = DataColors.rumble,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(
                    Icons.Rounded.GraphicEq,
                    contentDescription = "Save this clip",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

