package org.sovereignhq.sleepwave.alarm

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import org.sovereignhq.sleepwave.data.Settings
import org.sovereignhq.sleepwave.service.SleepService
import org.sovereignhq.sleepwave.service.SleepState
import org.sovereignhq.sleepwave.ui.components.AlarmSurface
import org.sovereignhq.sleepwave.ui.theme.SleepWaveTheme

/**
 * The wake-up screen, over the lock screen, with the display turned on.
 *
 * It never plays audio itself - the service owns the sound, so there is exactly one thing making
 * noise and exactly one thing to stop. When the backstop opened this screen because the service had
 * been killed, the first thing it does is hand control back to a fresh service instance, which is
 * allowed here because a visible activity counts as the app being in use.
 *
 * The controls themselves live in [AlarmSurface], shared with the main app, because Android 14 can
 * refuse this activity permission to appear at all - and when it does, the same buttons need to be
 * somewhere the user can actually reach.
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

        val settings = Settings(this)
        val puzzleDigits = settings.puzzleDigits
        val puzzleCount = settings.puzzleCount

        setContent {
            SleepWaveTheme {
                val ringing by SleepState.alarmRinging.collectAsState()
                val quiet by SleepState.alarmQuiet.collectAsState()

                LaunchedEffect(ringing) {
                    // The service is the source of truth: once it stops, this screen has no reason
                    // to exist. A short delay avoids closing before the service has even started.
                    if (!ringing) {
                        delay(1_500)
                        if (!SleepState.alarmRinging.value) finish()
                    }
                }

                AlarmSurface(
                    puzzleDigits = puzzleDigits,
                    puzzleCount = puzzleCount,
                    quietened = quiet,
                    onQuieten = { SleepService.send(this, SleepService.ACTION_QUIET) },
                    onSnooze = {
                        SleepService.send(this, SleepService.ACTION_SNOOZE)
                        finish()
                    },
                    onDismiss = {
                        SleepService.send(this, SleepService.ACTION_DISMISS)
                        finish()
                    }
                )
            }
        }
    }

    /**
     * Either volume key snoozes, which stays safe even with a puzzle set: a snooze brings the alarm
     * back, so it is not a way around the puzzle. Only solving the puzzle dismisses anything.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            SleepService.send(this, SleepService.ACTION_SNOOZE)
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        /** Set by the alarm backstop, meaning "the service was gone, you are the entry point". */
        const val ACTION_RESCUE = "org.sovereignhq.sleepwave.RESCUE"
    }
}
