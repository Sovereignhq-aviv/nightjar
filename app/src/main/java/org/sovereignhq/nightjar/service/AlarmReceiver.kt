package org.sovereignhq.nightjar.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.sovereignhq.nightjar.alarm.AlarmActivity

/**
 * Fires at the hard wake-up time registered with the system alarm clock.
 *
 * Two paths. Normally the tracking service is alive and simply gets told to ring, which gives the
 * fading volume ramp and the proper wake-up screen. If the service is gone - killed overnight by
 * a battery manager or low memory - this rings by itself using a notification channel that
 * carries the system alarm sound, and tries to open the wake-up screen on top of the lock screen.
 * The alarm is the one thing that must not depend on the app still being healthy.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RING) return

        if (SleepService.isRunning) {
            Log.i(TAG, "Handing the alarm to the running service")
            SleepService.send(context, SleepService.ACTION_FIRE_ALARM)
            return
        }

        Log.w(TAG, "Service was gone - ringing from the backstop")
        Notifications.createChannels(context)
        context.getSystemService(NotificationManager::class.java)
            ?.notify(Notifications.ID_ALARM, Notifications.alarm(context, fallbackSound = true))

        // An exact alarm grants a short window in which starting an activity is permitted.
        runCatching {
            context.startActivity(
                Intent(context, AlarmActivity::class.java)
                    .setAction(AlarmActivity.ACTION_RESCUE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
        }.onFailure { Log.w(TAG, "Could not open the wake-up screen", it) }
    }

    companion object {
        const val ACTION_RING = "org.sovereignhq.nightjar.RING"
        private const val TAG = "AlarmReceiver"
    }
}
