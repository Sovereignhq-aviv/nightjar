package org.sovereignhq.sleepwave.data

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar

/** Plain SharedPreferences. Small enough that a reactive store would be more code than value. */
class Settings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sleepwave", Context.MODE_PRIVATE)

    var alarmEnabled: Boolean
        get() = prefs.getBoolean("alarm_enabled", true)
        set(v) = prefs.edit().putBoolean("alarm_enabled", v).apply()

    var alarmHour: Int
        get() = prefs.getInt("alarm_hour", 7)
        set(v) = prefs.edit().putInt("alarm_hour", v).apply()

    var alarmMinute: Int
        get() = prefs.getInt("alarm_minute", 0)
        set(v) = prefs.edit().putInt("alarm_minute", v).apply()

    /** How far before [alarmHour]:[alarmMinute] the smart alarm may wake you. */
    var windowMinutes: Int
        get() = prefs.getInt("window_minutes", 30)
        set(v) = prefs.edit().putInt("window_minutes", v).apply()

    var snoozeMinutes: Int
        get() = prefs.getInt("snooze_minutes", 9)
        set(v) = prefs.edit().putInt("snooze_minutes", v).apply()

    var sleepGoalMinutes: Int
        get() = prefs.getInt("sleep_goal_minutes", 8 * 60)
        set(v) = prefs.edit().putInt("sleep_goal_minutes", v).apply()

    var recordSnoring: Boolean
        get() = prefs.getBoolean("record_snoring", true)
        set(v) = prefs.edit().putBoolean("record_snoring", v).apply()

    var motionSensing: Boolean
        get() = prefs.getBoolean("motion_sensing", true)
        set(v) = prefs.edit().putBoolean("motion_sensing", v).apply()

    var vibrate: Boolean
        get() = prefs.getBoolean("vibrate", true)
        set(v) = prefs.edit().putBoolean("vibrate", v).apply()

    /** Seconds the alarm takes to ramp from near-silent to full volume. */
    var rampSeconds: Int
        get() = prefs.getInt("ramp_seconds", 45)
        set(v) = prefs.edit().putInt("ramp_seconds", v).apply()

    /** Empty means "use the system default alarm sound". */
    var alarmSoundUri: String
        get() = prefs.getString("alarm_sound_uri", "") ?: ""
        set(v) = prefs.edit().putString("alarm_sound_uri", v).apply()

    var autoDeleteDays: Int
        get() = prefs.getInt("auto_delete_days", 30)
        set(v) = prefs.edit().putInt("auto_delete_days", v).apply()

    /** Session id currently being tracked, or empty. Survives the app being killed. */
    var activeSessionId: String
        get() = prefs.getString("active_session_id", "") ?: ""
        set(v) = prefs.edit().putString("active_session_id", v).apply()

    var activeAlarmTargetMs: Long
        get() = prefs.getLong("active_alarm_target", 0L)
        set(v) = prefs.edit().putLong("active_alarm_target", v).apply()

    /** The next occurrence of the alarm time, always in the future. */
    fun nextAlarmTimeMs(from: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, alarmHour)
            set(Calendar.MINUTE, alarmMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= from) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }
}
