package org.sovereignhq.sleepwave.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.sovereignhq.sleepwave.data.Settings

/**
 * System alarms do not survive a reboot. If the phone restarts mid-night, tracking is lost -
 * nothing can be done about that - but the wake-up time is re-registered so the alarm still rings.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val settings = Settings(context)
        val target = settings.activeAlarmTargetMs
        if (target <= System.currentTimeMillis()) {
            settings.activeSessionId = ""
            settings.activeAlarmTargetMs = 0L
            return
        }

        Log.i(TAG, "Re-arming the alarm after reboot for $target")
        Notifications.createChannels(context)
        AlarmScheduler.schedule(context, target)
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
