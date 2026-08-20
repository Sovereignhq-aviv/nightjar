package org.sovereignhq.nightjar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.sovereignhq.nightjar.service.SleepService
import org.sovereignhq.nightjar.service.SleepState
import org.sovereignhq.nightjar.ui.NightScreen
import org.sovereignhq.nightjar.ui.SettingsScreen
import org.sovereignhq.nightjar.ui.SleepScreen
import org.sovereignhq.nightjar.ui.SleepViewModel
import org.sovereignhq.nightjar.ui.SoundsScreen
import org.sovereignhq.nightjar.ui.TrendsScreen
import org.sovereignhq.nightjar.ui.components.AlarmSurface
import org.sovereignhq.nightjar.ui.components.DockedPlayer

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind the status and navigation bars; Scaffold and NavigationBar hand the insets
        // back so nothing important ends up underneath them.
        enableEdgeToEdge()
        setContent {
            org.sovereignhq.nightjar.ui.theme.NightjarTheme {
                NightjarApp()
            }
        }
    }
}

private enum class Tab { SOUNDS, SLEEP, TRENDS, SETTINGS }

@Composable
private fun NightjarApp() {
    val context = LocalContext.current
    val vm: SleepViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val snackbars = remember { SnackbarHostState() }

    var tab by remember { mutableStateOf(Tab.SOUNDS) }
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val tracking by SleepState.tracking.collectAsState()
    val alarmRinging by SleepState.alarmRinging.collectAsState()
    val alarmQuiet by SleepState.alarmQuiet.collectAsState()
    val finishedSessionId by SleepState.finishedSessionId.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) {
            micGranted = true
            SleepService.start(context)
        }
    }

    fun startNight() {
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) SleepService.start(context)
        else permissionLauncher.launch(missing.toTypedArray())
    }

    // A finished night lands on its recordings, which is the first thing anyone wants in the morning.
    LaunchedEffect(finishedSessionId) {
        finishedSessionId?.let { id ->
            vm.refresh()
            vm.selectSession(id)
            tab = Tab.SOUNDS
            SleepState.consumeFinishedSession()
        }
    }

    LaunchedEffect(tracking) { if (!tracking) vm.refresh() }

    // A ringing alarm outranks everything. This branch is why the notification shade is no longer
    // the only place with a stop button: Android 14 can refuse the full-screen wake-up activity
    // permission to appear, and when it does, opening the app has to be enough.
    if (alarmRinging) {
        AlarmSurface(
            puzzleDigits = vm.settings.puzzleDigits,
            puzzleCount = vm.settings.puzzleCount,
            quietened = alarmQuiet,
            onQuieten = { SleepService.send(context, SleepService.ACTION_QUIET) },
            onSnooze = { SleepService.send(context, SleepService.ACTION_SNOOZE) },
            onDismiss = { SleepService.send(context, SleepService.ACTION_DISMISS) }
        )
        return
    }

    if (tracking) {
        val startedAt by SleepState.startedAtMs.collectAsState()
        val alarmTarget by SleepState.alarmTargetMs.collectAsState()
        val live by SleepState.liveActivity.collectAsState()
        val level by SleepState.level.collectAsState()
        val snore by SleepState.snoreMinutes.collectAsState()
        val clipCount by SleepState.clipCount.collectAsState()
        val error by SleepState.error.collectAsState()

        NightScreen(
            startedAtMs = startedAt,
            alarmTargetMs = alarmTarget,
            liveActivity = live,
            level = level,
            snoreMinutes = snore,
            clipCount = clipCount,
            error = error,
            onStop = { SleepService.send(context, SleepService.ACTION_STOP) }
        )
        return
    }

    BackHandler(enabled = tab != Tab.SOUNDS) { tab = Tab.SOUNDS }

    val session = vm.selectedSession

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbars) },
        bottomBar = {
            Column {
                DockedPlayer(
                    clip = vm.player.current,
                    playing = vm.player.playing,
                    progress = vm.player.progress,
                    speed = vm.player.speed,
                    queueSize = vm.player.queueSize,
                    onTogglePlay = {
                        if (vm.player.playing) vm.player.pause() else vm.player.resume()
                    },
                    onPrevious = { vm.player.previous() },
                    onNext = { vm.player.next() },
                    onSeek = { vm.player.seekTo(it) },
                    onCycleSpeed = { vm.player.cycleSpeed() },
                    onToggleStar = {
                        val clip = vm.player.current
                        if (session != null && clip != null) vm.toggleStar(session, clip)
                    },
                    onShare = {
                        val clip = vm.player.current
                        val intent = clip?.let { vm.shareIntent(it) }
                        if (intent == null) {
                            scope.launch { snackbars.showSnackbar("That recording is no longer on the phone.") }
                        } else {
                            context.startActivity(Intent.createChooser(intent, "Share recording"))
                        }
                    },
                    onClose = { vm.player.stop() },
                    onCorrectLabel = { kind ->
                        val clip = vm.player.current
                        if (session != null && clip != null) vm.correctLabel(session, clip, kind)
                    },
                    onMuteDetail = { label ->
                        vm.muteDetail(label)
                        scope.launch {
                            snackbars.showSnackbar("\"$label\" will not be recorded again.")
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavBarItem(Tab.SOUNDS, tab, "Sounds") { tab = it }
                    NavBarItem(Tab.SLEEP, tab, "Sleep") { tab = it }
                    NavBarItem(Tab.TRENDS, tab, "Trends") { tab = it }
                    NavBarItem(Tab.SETTINGS, tab, "Settings") { tab = it }
                }
            }
        },
        floatingActionButton = {
            val clips = session?.clips ?: emptyList()
            if (micGranted && tab == Tab.SOUNDS && clips.size >= 2 && vm.player.current == null) {
                ExtendedFloatingActionButton(
                    onClick = { session?.let { vm.playHighlights(it) } },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Rounded.PlaylistPlay, contentDescription = null) },
                    text = { Text("Highlights") }
                )
            }
        }
    ) { insets ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(insets)
        ) {
            if (!micGranted) {
                PermissionGate { permissionLauncher.launch(requiredPermissions()) }
                return@Box
            }

            // Fade-through between destinations: Material's pattern for switching between
            // unrelated top-level screens.
            Crossfade(targetState = tab, animationSpec = tween(200), label = "tab") { visible ->
                when (visible) {
                    Tab.SOUNDS -> SoundsScreen(
                        vm = vm,
                        tracking = false,
                        onStartNight = { startNight() }
                    )

                    Tab.SLEEP -> SleepScreen(vm = vm, onStartNight = { startNight() })

                    Tab.TRENDS -> TrendsScreen(
                        vm = vm,
                        onOpenNight = { id ->
                            vm.selectSession(id)
                            tab = Tab.SOUNDS
                        }
                    )

                    Tab.SETTINGS -> SettingsScreen(vm = vm)
                }
            }
        }
    }
}

@Composable
private fun RowScope.NavBarItem(
    target: Tab,
    current: Tab,
    label: String,
    onSelect: (Tab) -> Unit
) {
    NavigationBarItem(
        selected = current == target,
        onClick = { onSelect(target) },
        icon = {
            Icon(
                imageVector = when (target) {
                    Tab.SOUNDS -> Icons.Rounded.GraphicEq
                    Tab.SLEEP -> Icons.Rounded.Bedtime
                    Tab.TRENDS -> Icons.Rounded.ShowChart
                    Tab.SETTINGS -> Icons.Rounded.Settings
                },
                contentDescription = null
            )
        },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@Composable
private fun PermissionGate(onGrant: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Nightjar needs the microphone", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            "It listens through the night for movement, breathing and anything that stands out. " +
                "Audio is analysed on the phone and continuously thrown away; the only thing saved " +
                "is a short clip when something crosses the threshold, and you can turn that off.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onGrant,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Allow microphone")
        }
    }
}

private fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf(Manifest.permission.RECORD_AUDIO)
    }
