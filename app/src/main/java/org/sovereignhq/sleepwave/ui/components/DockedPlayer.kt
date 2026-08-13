package org.sovereignhq.sleepwave.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.sovereignhq.sleepwave.data.SoundClip
import org.sovereignhq.sleepwave.ui.eventLabel
import org.sovereignhq.sleepwave.ui.formatClock
import org.sovereignhq.sleepwave.ui.theme.DataColors

/**
 * The playback surface, docked above the navigation bar.
 *
 * It lives here rather than inside the row you tapped because a night can hold a hundred
 * recordings: the moment you scroll, an in-row player is gone and you are hunting for the stop
 * button. Docked, the controls stay under your thumb wherever you are in the list.
 */
@Composable
fun DockedPlayer(
    clip: SoundClip?,
    playing: Boolean,
    progress: Float,
    speed: Float,
    queueSize: Int,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit,
    onCycleSpeed: () -> Unit,
    onToggleStar: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = clip != null,
        enter = slideInVertically(animationSpec = tween(220)) { it } + fadeIn(tween(220)),
        exit = slideOutVertically(animationSpec = tween(180)) { it } + fadeOut(tween(180)),
        modifier = modifier
    ) {
        val shown = clip ?: return@AnimatedVisibility
        val accent = DataColors.forEvent(shown.eventKind)

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = eventLabel(shown.eventKind),
                    style = MaterialTheme.typography.titleSmall,
                    color = accent
                )
                Text(
                    text = "  ·  ${formatClock(shown.startedAtMs)}  ·  ${shown.durationMs / 1000}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.weight(1f))

                IconButton(onClick = onToggleStar, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = if (shown.starred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = if (shown.starred) "Remove from saved" else "Save this clip",
                        tint = if (shown.starred) DataColors.rumble else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onShare, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Rounded.Share,
                        contentDescription = "Share this clip",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Close the player",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (queueSize > 1) {
                    IconButton(onClick = onPrevious, modifier = Modifier.size(44.dp)) {
                        Icon(
                            Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous recording",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(accent)
                        .clickable(onClick = onTogglePlay),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }

                if (queueSize > 1) {
                    IconButton(onClick = onNext, modifier = Modifier.size(44.dp)) {
                        Icon(
                            Icons.Rounded.SkipNext,
                            contentDescription = "Next recording",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                Waveform(
                    envelope = shown.envelope,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    color = accent,
                    progress = progress,
                    bars = 60,
                    onSeek = onSeek
                )

                Spacer(Modifier.width(8.dp))

                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(onClick = onCycleSpeed)
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = if (speed == 1f) "1x" else if (speed == 1.5f) "1.5x" else "2x",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
