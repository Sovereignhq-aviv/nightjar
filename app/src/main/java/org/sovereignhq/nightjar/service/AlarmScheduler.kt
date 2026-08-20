package org.sovereignhq.nightjar.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import org.sovereignhq.nightjar.MainActivity

/**
 * The hard deadline behind the smart alarm.
 *
 * The tracking service normally decides when to ring, but a service can be killed - low memory,
 * an aggressive battery manager, a crash. This registers the same wake-up time with the system
 * alarm clock, which survives all of that. If the service is alive when it fires, nothing is
 * lost; if it is not, the alarm still rings.
 */
object AlarmScheduler {

    private const val TAG = "AlarmScheduler"
    private const val REQUEST_BACKSTOP = 100

    fun schedule(context: Context, triggerAtMs: Long) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val operation = backstopIntent(context)

        try {
            if (canScheduleExact(manager)) {
                // setAlarmClock is the highest-priority alarm Android offers: never deferred by
                // Doze, and it shows the alarm icon in the status bar as visible confirmation.
                val show = PendingIntent.getActivity(
                    context, REQUEST_BACKSTOP + 1,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                manager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMs, show), operation)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, operation)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarms not permitted, falling back", e)
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, operation)
        }
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        manager.cancel(backstopIntent(context))
    }

    fun canScheduleExact(manager: AlarmManager): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) manager.canScheduleExactAlarms() else true

    fun canScheduleExact(context: Context): Boolean {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        return canScheduleExact(manager)
    }

    private fun backstopIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST_BACKSTOP,
        Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_RING),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
