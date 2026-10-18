package org.sovereignhq.nightjar.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import org.sovereignhq.nightjar.service.AlarmScheduler
import org.sovereignhq.nightjar.service.Notifications

/**
 * The three Android settings that quietly break an overnight alarm, with the check and the fix for
 * each in one place. Settings, the Sleep tab's warning and the first-run setup all read these, so a
 * fix that changes here changes everywhere.
 */
object Reliability {

    fun batteryUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true

    /**
     * Asks for the exemption directly: one "Allow" in a system dialog, instead of hunting for the app
     * in a list of every installed app. The list is the fallback for phones that refuse the dialog.
     */
    fun openBatterySettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:${context.packageName}"))
            )
        }.recoverCatching {
            context.startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    fun exactAlarmsAllowed(context: Context): Boolean = AlarmScheduler.canScheduleExact(context)

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            context.startActivity(
                Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(Uri.parse("package:${context.packageName}"))
            )
        }
    }

    fun wakeScreenAllowed(context: Context): Boolean = Notifications.canShowFullScreenAlarm(context)

    fun openWakeScreenSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        runCatching {
            context.startActivity(
                Intent(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                    .setData(Uri.parse("package:${context.packageName}"))
            )
        }
    }

    fun allGood(context: Context): Boolean =
        batteryUnrestricted(context) && exactAlarmsAllowed(context) && wakeScreenAllowed(context)
}
