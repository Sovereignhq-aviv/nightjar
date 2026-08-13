package org.sovereignhq.sleepwave

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import org.sovereignhq.sleepwave.service.SleepService
import org.sovereignhq.sleepwave.service.SleepState
import org.sovereignhq.sleepwave.ui.HomeScreen
import org.sovereignhq.sleepwave.ui.MorningScreen
import org.sovereignhq.sleepwave.ui.NightScreen
import org.sovereignhq.sleepwave.ui.SettingsScreen
import org.sovereignhq.sleepwave.ui.SleepViewModel
import org.sovereignhq.sleepwave.ui.TrendsScreen
import org.sovereignhq.sleepwave.ui.theme.Indigo
import org.sovereignhq.sleepwave.ui.theme.NightBg
import org.sovereignhq.sleepwave.ui.theme.NightSurface
import org.sovereignhq.sleepwave.ui.theme.SleepWaveTheme
import org.sovereignhq.sleepwave.ui.theme.TextMuted

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SleepWaveTheme {
                Box(Modifier.fillMaxSize().background(NightBg)) {
                    SleepWaveApp()
                }
            }
        }
    }
}

private enum class Tab { SLEEP, TRENDS, SETTINGS }

@Composable
private fun SleepWaveApp() {
    val context = LocalContext.current
    val vm: SleepViewModel = viewModel()

    var tab by remember { mutableStateOf(Tab.SLEEP) }
    var openSessionId by remember { mutableStateOf<String?>(null) }
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val tracking by SleepState.tracking.collectAsState()
    val finishedSessionId by SleepState.finishedSessionId.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) {
            micGranted = true
            SleepService.start(context)
        }
    }

    // A finished night jumps straight to its report - that is the first thing anyone wants to see.
    LaunchedEffect(finishedSessionId) {
        finishedSessionId?.let { id ->
            vm.refresh()
            openSessionId = id
            tab = Tab.SLEEP
            SleepState.consumeFinishedSession()
        }
    }

    LaunchedEffect(tracking) {
        if (!tracking) vm.refresh()
    }

    if (tracking) {
        val startedAt by SleepState.startedAtMs.collectAsState()
        val alarmTarget by SleepState.alarmTargetMs.collectAsState()
        val live by SleepState.liveActivity.collectAsState()
        val level by SleepState.level.collectAsState()
        val snore by SleepState.snoreMinutes.collectAsState()
        val error by SleepState.error.collectAsState()

        NightScreen(
            startedAtMs = startedAt,
            alarmTargetMs = alarmTarget,
            liveActivity = live,
            level = level,
            snoreMinutes = snore,
            error = error,
            onStop = { SleepService.send(context, SleepService.ACTION_STOP) }
        )
        return
    }

    Scaffold(
        containerColor = NightBg,
        bottomBar = {
            NavigationBar(containerColor = NightSurface) {
                NavBarItem(Tab.SLEEP, tab, "Sleep") { tab = it; openSessionId = null }
                NavBarItem(Tab.TRENDS, tab, "Trends") { tab = it }
                NavBarItem(Tab.SETTINGS, tab, "Settings") { tab = it }
            }
        }
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets)) {
            when {
                !micGranted -> PermissionGate {
                    permissionLauncher.launch(requiredPermissions())
                }

                tab == Tab.SLEEP && openSessionId != null -> {
                    val session = vm.session(openSessionId!!)
                    // A night can vanish underneath us if it was just deleted or pruned.
                    LaunchedEffect(session) { if (session == null) openSessionId = null }
                    if (session != null) {
                        MorningScreen(
                            vm = vm,
                            session = session,
                            onBack = { openSessionId = null },
                            onDeleted = { openSessionId = null }
                        )
                    }
                }

                tab == Tab.SLEEP -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    HomeScreen(
                        vm = vm,
                        onStartNight = {
                            val missing = requiredPermissions().filter {
                                ContextCompat.checkSelfPermission(context, it) !=
                                    PackageManager.PERMISSION_GRANTED
                            }
                            if (missing.isEmpty()) {
                                SleepService.start(context)
                            } else {
                                permissionLauncher.launch(missing.toTypedArray())
                            }
                        },
                        onOpenSession = { openSessionId = it }
                    )
                }

                tab == Tab.TRENDS -> TrendsScreen(
                    vm = vm,
                    onOpenSession = { openSessionId = it; tab = Tab.SLEEP }
                )

                else -> SettingsScreen(vm = vm)
            }
        }
    }
}

@Composable
private fun NavBarItem(
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
                    Tab.SLEEP -> Icons.Rounded.Bedtime
                    Tab.TRENDS -> Icons.Rounded.BarChart
                    Tab.SETTINGS -> Icons.Rounded.Settings
                },
                contentDescription = label
            )
        },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
            selectedTextColor = Indigo,
            indicatorColor = Indigo,
            unselectedIconColor = TextMuted,
            unselectedTextColor = TextMuted
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
        Text("SleepWave needs the microphone", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            "Sleep is tracked by listening for movement and breathing through the night. " +
                "Audio is analysed on the phone and thrown away moment to moment - the only " +
                "thing ever saved is a short clip when snoring is detected, and you can turn " +
                "that off.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onGrant,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Indigo)
        ) {
            Text("Allow microphone", color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

private fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf(Manifest.permission.RECORD_AUDIO)
    }
