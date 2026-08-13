package org.sovereignhq.sleepwave.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import org.sovereignhq.sleepwave.MainActivity
import org.sovereignhq.sleepwave.R
import org.sovereignhq.sleepwave.alarm.AlarmActivity

object Notifications {

    const val CHANNEL_TRACKING = "tracking"

    /** Silent on purpose: AlarmPlayer owns the sound so it can fade in and loop. */
    const val CHANNEL_ALARM = "alarm"

    /**
     * Used only when the tracking service is gone and nothing in the app is able to play audio.
     * This channel carries the system alarm sound itself, so the phone still makes a noise.
     */
    const val CHANNEL_ALARM_FALLBACK = "alarm_fallback"

    const val ID_TRACKING = 1001
    const val ID_ALARM = 1002

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TRACKING,
                context.getString(R.string.channel_tracking_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_tracking_desc)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALARM,
                context.getString(R.string.channel_alarm_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_alarm_desc)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALARM_FALLBACK,
                "Alarm (backup)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Rings if SleepWave was shut down by the system overnight."
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
        )
    }

    fun tracking(context: Context, text: String): Notification {
        val stop = PendingIntent.getService(
            context, 1,
            Intent(context, SleepService::class.java).setAction(SleepService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(context, CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_stat_night)
            .setContentTitle("Tracking your sleep")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(openApp(context))
            .addAction(Notification.Action.Builder(null, "Stop tracking", stop).build())
            .build()
    }

    fun alarm(context: Context, fallbackSound: Boolean = false): Notification {
        val snooze = PendingIntent.getService(
            context, 3,
            Intent(context, SleepService::class.java).setAction(SleepService.ACTION_SNOOZE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismiss = PendingIntent.getService(
            context, 4,
            Intent(context, SleepService::class.java).setAction(SleepService.ACTION_DISMISS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val fullScreen = alarmScreenIntent(context)

        return Notification.Builder(
            context,
            if (fallbackSound) CHANNEL_ALARM_FALLBACK else CHANNEL_ALARM
        )
            .setSmallIcon(R.drawable.ic_stat_night)
            .setContentTitle("Good morning")
            .setContentText("Tap to stop the alarm")
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .addAction(Notification.Action.Builder(null, "Snooze", snooze).build())
            .addAction(Notification.Action.Builder(null, "Stop", dismiss).build())
            .build()
    }

    fun alarmScreenIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 2,
        Intent(context, AlarmActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun cancelAlarm(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ID_ALARM)
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
