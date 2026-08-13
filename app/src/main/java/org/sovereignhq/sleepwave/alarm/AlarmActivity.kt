package org.sovereignhq.sleepwave.alarm

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.sovereignhq.sleepwave.service.SleepService
import org.sovereignhq.sleepwave.service.SleepState
import org.sovereignhq.sleepwave.ui.formatClock
import org.sovereignhq.sleepwave.ui.theme.Indigo
import org.sovereignhq.sleepwave.ui.theme.NightBg
import org.sovereignhq.sleepwave.ui.theme.SleepWaveTheme
import org.sovereignhq.sleepwave.ui.theme.TextMuted

/**
 * The wake-up screen. Appears over the lock screen and turns the display on.
 *
 * It never plays audio itself - the service owns the sound so there is exactly one thing making
 * noise and exactly one thing to stop. When the backstop opened this screen because the service
 * had been killed, the first thing it does is hand control back to a fresh service instance,
 * which is allowed here because a visible activity counts as the app being in use.
 */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (!SleepState.alarmRinging.value) {
            SleepService.send(this, SleepService.ACTION_FIRE_ALARM)
        }

        // Dismissing an alarm should take a decision, not a stray back swipe.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        setContent {
            SleepWaveTheme {
                val ringing by SleepState.alarmRinging.collectAsState()

                LaunchedEffect(ringing) {
                    // The service is the source of truth: once it stops, this screen has no reason
                    // to exist. A short delay avoids closing before the service has even started.
                    if (!ringing) {
                        delay(1_500)
                        if (!SleepState.alarmRinging.value) finish()
                    }
                }

                AlarmScreen(
                    onSnooze = { SleepService.send(this, SleepService.ACTION_SNOOZE); finish() },
                    onStop = { SleepService.send(this, SleepService.ACTION_DISMISS); finish() }
                )
            }
        }
    }

    companion object {
        /** Set by the alarm backstop, meaning "the service was gone, you are the entry point". */
        const val ACTION_RESCUE = "org.sovereignhq.sleepwave.RESCUE"
    }
}

@Composable
private fun AlarmScreen(onSnooze: () -> Unit, onStop: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(5_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NightBg)
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Good morning", style = MaterialTheme.typography.titleLarge, color = TextMuted)
        Spacer(Modifier.height(8.dp))
        Text(formatClock(now), style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            "Your night is saved. Open SleepWave to see it.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(56.dp))

        Button(
            onClick = onStop,
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Indigo)
        ) {
            Text(
                "I'm up",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onSnooze,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text("Snooze", style = MaterialTheme.typography.titleMedium, color = TextMuted)
        }
    }
}
