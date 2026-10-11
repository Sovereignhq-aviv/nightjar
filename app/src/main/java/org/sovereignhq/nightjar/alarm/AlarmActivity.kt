package org.sovereignhq.nightjar.alarm

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
import org.sovereignhq.nightjar.data.Settings
import org.sovereignhq.nightjar.service.SleepService
import org.sovereignhq.nightjar.service.SleepState
import org.sovereignhq.nightjar.ui.components.AlarmSurface
import org.sovereignhq.nightjar.ui.theme.NightjarTheme

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
        // Bright enough to read a sum, dim enough not to light up the room for whoever is still
        // asleep. The system brightness is left alone; this only applies while the alarm is up.
        window.attributes = window.attributes.apply { screenBrightness = ALARM_BRIGHTNESS }

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
        val wakeCode = settings.wakeCode
        val wakePhotoRefs = settings.wakePhotoRefs
        val wakePhotoThreshold = settings.wakePhotoThreshold

        setContent {
            NightjarTheme {
                val ringing by SleepState.alarmRinging.collectAsState()
                val quietUntil by SleepState.alarmQuietUntilMs.collectAsState()

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
                    wakeCode = wakeCode,
                    wakePhotoRefs = wakePhotoRefs,
                    wakePhotoThreshold = wakePhotoThreshold,
                    quietUntilMs = quietUntil,
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
     * Either volume key quietens the alarm, exactly like the on-screen "Quiet it" button.
     *
     * It used to snooze, which threw you off the sums or the scan halfway through: the instinctive
     * grab for a volume button means "make it stop for a moment", not "start again in nine minutes".
     * Quietening keeps the alarm armed and keeps whatever you were solving on screen, and the sound
     * comes back on its own if you then stop. Snoozing is still one tap away on the screen.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            SleepService.send(this, SleepService.ACTION_QUIET)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        /** Set by the alarm backstop, meaning "the service was gone, you are the entry point". */
        const val ACTION_RESCUE = "org.sovereignhq.nightjar.RESCUE"

        private const val ALARM_BRIGHTNESS = 0.25f
    }
}
