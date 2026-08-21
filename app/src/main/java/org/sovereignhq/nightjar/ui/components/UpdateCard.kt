package org.sovereignhq.nightjar.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.sovereignhq.nightjar.ui.UpdateState

/**
 * The self-update control, in Settings.
 *
 * Every step is something the user starts: check, download, install. Nothing downloads on a mobile
 * connection without being asked, and the install always goes through Android's own confirmation.
 * An app that could silently replace itself would be indistinguishable from malware, so it cannot.
 */
@Composable
fun UpdateCard(
    state: UpdateState,
    installedVersion: String,
    autoCheckEnabled: Boolean,
    onToggleAutoCheck: (Boolean) -> Unit,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val canInstall = context.packageManager.canRequestPackageInstalls()

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Version $installedVersion", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = when (state) {
                        is UpdateState.Checking -> "Looking for a newer one…"
                        is UpdateState.UpToDate -> "This is the newest version."
                        is UpdateState.Available -> "Version ${state.release.version} is available."
                        is UpdateState.Downloading ->
                            "Downloading… ${(state.progress * 100).toInt()}%"
                        is UpdateState.Ready -> "Version ${state.version} is ready to install."
                        is UpdateState.Failed -> state.message
                        UpdateState.Idle -> "Installed from GitHub, updated from GitHub."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (state) {
                        is UpdateState.Available, is UpdateState.Ready ->
                            MaterialTheme.colorScheme.primary
                        is UpdateState.Failed -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            if (state is UpdateState.Available || state is UpdateState.Failed) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        AnimatedVisibility(visible = state is UpdateState.Downloading) {
            val progress = (state as? UpdateState.Downloading)?.progress ?: 0f
            Column {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }
        }

        if (state is UpdateState.Available && state.release.notes.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = state.release.notes.lineSequence().take(6).joinToString("\n").trim(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(14.dp))

        when (state) {
            is UpdateState.Available -> {
                Button(
                    onClick = onDownload,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null)
                    Text("  Download ${sizeLabel(state.release.sizeBytes)}")
                }
            }

            is UpdateState.Ready -> {
                if (canInstall) {
                    Button(
                        onClick = onInstall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) { Text("Install version ${state.version}") }
                } else {
                    // Installing an APK needs a per-app permission of its own. Without it the
                    // installer opens onto an error, which looks like the app is broken.
                    Text(
                        "Android needs permission to let Nightjar install an update. One tap, once.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                                        .setData(Uri.parse("package:${context.packageName}"))
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) { Text("Allow installing updates") }
                }
            }

            is UpdateState.Downloading -> Unit

            else -> {
                OutlinedButton(
                    onClick = onCheck,
                    enabled = state !is UpdateState.Checking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(if (state is UpdateState.Checking) "Checking…" else "Check for updates")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        HairLine()
        Spacer(Modifier.height(14.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.fillMaxWidth(0.76f)) {
                Text("Check automatically", style = MaterialTheme.typography.titleMedium)
                Text(
                    "A few times a day, when the app is open. This is the only thing Nightjar ever " +
                        "sends over the network - a plain request to GitHub asking what the newest " +
                        "version is. Nothing about you or your recordings goes with it. Switch this " +
                        "off and the app makes no network requests at all.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            androidx.compose.material3.Switch(
                checked = autoCheckEnabled,
                onCheckedChange = onToggleAutoCheck
            )
        }
    }
}

/** A one-line nudge for the top of the Sounds screen, so an update is not hidden in Settings. */
@Composable
fun UpdateBanner(
    state: UpdateState,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val version = when (state) {
        is UpdateState.Available -> state.release.version
        is UpdateState.Ready -> state.version
        else -> null
    } ?: return

    val ready = state is UpdateState.Ready

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = if (ready) {
                "Version $version downloaded — tap to install"
            } else {
                "Version $version available"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Icon(
            Icons.Rounded.Download,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun sizeLabel(bytes: Long): String =
    if (bytes <= 0) "" else "(${bytes / 1_000_000} MB)"
