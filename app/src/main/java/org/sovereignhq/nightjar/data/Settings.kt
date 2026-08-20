package org.sovereignhq.nightjar.data

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar

/**
 * How eagerly the app saves a recording.
 *
 * [triggerDb] is measured against the room's own background level, not an absolute volume, so the
 * same setting behaves the same in a city flat and a quiet cottage.
 */
enum class Sensitivity(
    val label: String,
    val triggerDb: Float,
    val maxClipsPerNight: Int,
    val blurb: String
) {
    LOOSE(
        label = "Catch a lot",
        triggerDb = 12f,
        maxClipsPerNight = 120,
        blurb = "Picks up quiet rumbles and mumbling. Also picks up traffic and the air conditioning."
    ),
    BALANCED(
        label = "Balanced",
        triggerDb = 16f,
        maxClipsPerNight = 60,
        blurb = "Clear snoring, talking and obvious outbursts. Misses the quiet ones."
    ),
    STRICT(
        label = "Only the obvious",
        triggerDb = 22f,
        maxClipsPerNight = 30,
        blurb = "Loud and unmistakable only. Almost never a false alarm."
    );

    companion object {
        fun from(name: String): Sensitivity = entries.firstOrNull { it.name == name } ?: LOOSE
    }
}

/** Plain SharedPreferences. Small enough that a reactive store would be more code than value. */
class Settings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nightjar", Context.MODE_PRIVATE)

    var alarmEnabled: Boolean
        get() = prefs.getBoolean("alarm_enabled", true)
        set(v) = prefs.edit().putBoolean("alarm_enabled", v).apply()

    var alarmHour: Int
        get() = prefs.getInt("alarm_hour", 7)
        set(v) = prefs.edit().putInt("alarm_hour", v).apply()

    var alarmMinute: Int
        get() = prefs.getInt("alarm_minute", 0)
        set(v) = prefs.edit().putInt("alarm_minute", v).apply()

    /** How far before the alarm time the smart alarm may wake you. */
    var windowMinutes: Int
        get() = prefs.getInt("window_minutes", 30)
        set(v) = prefs.edit().putInt("window_minutes", v).apply()

    var snoozeMinutes: Int
        get() = prefs.getInt("snooze_minutes", 9)
        set(v) = prefs.edit().putInt("snooze_minutes", v).apply()

    var sleepGoalMinutes: Int
        get() = prefs.getInt("sleep_goal_minutes", 8 * 60)
        set(v) = prefs.edit().putInt("sleep_goal_minutes", v).apply()

    var recordSounds: Boolean
        get() = prefs.getBoolean("record_sounds", true)
        set(v) = prefs.edit().putBoolean("record_sounds", v).apply()

    var sensitivity: Sensitivity
        get() = Sensitivity.from(prefs.getString("sensitivity", Sensitivity.LOOSE.name) ?: "")
        set(v) = prefs.edit().putString("sensitivity", v.name).apply()

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

    /**
     * Digits in each arithmetic puzzle required to switch the alarm off. Zero means no puzzle.
     * The point is a task a thumb cannot complete while the rest of you stays asleep.
     */
    var puzzleDigits: Int
        get() = prefs.getInt("puzzle_digits", 0)
        set(v) = prefs.edit().putInt("puzzle_digits", v.coerceIn(0, 3)).apply()

    /** How many puzzles in a row. */
    var puzzleCount: Int
        get() = prefs.getInt("puzzle_count", 1)
        set(v) = prefs.edit().putInt("puzzle_count", v.coerceIn(1, 5)).apply()

    /** Empty means "use the system default alarm sound". */
    var alarmSoundUri: String
        get() = prefs.getString("alarm_sound_uri", "") ?: ""
        set(v) = prefs.edit().putString("alarm_sound_uri", v).apply()

    /**
     * Friendly name for [alarmSoundUri]. Stored rather than looked up because a document picked from
     * storage has no title a RingtoneManager can read, and re-querying the content resolver on every
     * recomposition to draw one row of settings would be absurd.
     */
    var alarmSoundLabel: String
        get() = prefs.getString("alarm_sound_label", "") ?: ""
        set(v) = prefs.edit().putString("alarm_sound_label", v).apply()

    /**
     * Model labels never worth recording again - "Air conditioning", "Mechanical fan". Separated by
     * a pipe because several AudioSet names contain commas ("Thump, thud").
     */
    var mutedLabels: Set<String>
        get() = (prefs.getString("muted_labels", "") ?: "")
            .split("|")
            .filter { it.isNotBlank() }
            .toSet()
        set(v) = prefs.edit().putString("muted_labels", v.joinToString("|")).apply()

    /**
     * Audio is the expensive thing on disk, so it goes first and it goes quickly.
     * Starred clips are exempt.
     */
    var clipRetentionDays: Int
        get() = prefs.getInt("clip_retention_days", 7)
        set(v) = prefs.edit().putInt("clip_retention_days", v).apply()

    /**
     * The graphs and scores are a few hundred KB a year, and Trends needs history to be worth
     * anything, so nights outlive their audio by a long way.
     */
    var nightRetentionDays: Int
        get() = prefs.getInt("night_retention_days", 365)
        set(v) = prefs.edit().putInt("night_retention_days", v).apply()

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

    /** A single immutable read of everything the UI shows, so Compose has one thing to observe. */
    fun snapshot(): SettingsSnapshot = SettingsSnapshot(
        alarmEnabled = alarmEnabled,
        alarmHour = alarmHour,
        alarmMinute = alarmMinute,
        windowMinutes = windowMinutes,
        snoozeMinutes = snoozeMinutes,
        sleepGoalMinutes = sleepGoalMinutes,
        recordSounds = recordSounds,
        sensitivity = sensitivity,
        motionSensing = motionSensing,
        vibrate = vibrate,
        rampSeconds = rampSeconds,
        puzzleDigits = puzzleDigits,
        puzzleCount = puzzleCount,
        alarmSoundUri = alarmSoundUri,
        alarmSoundLabel = alarmSoundLabel,
        mutedLabels = mutedLabels,
        clipRetentionDays = clipRetentionDays,
        nightRetentionDays = nightRetentionDays
    )
}

/**
 * Immutable mirror of [Settings].
 *
 * The UI edits it with `copy(...)` and hands the result back, which is why there are no
 * per-field setter methods: an earlier version had `setWindowMinutes(...)` next to a
 * `windowMinutes` property and the two collided on the same JVM signature.
 */
data class SettingsSnapshot(
    val alarmEnabled: Boolean,
    val alarmHour: Int,
    val alarmMinute: Int,
    val windowMinutes: Int,
    val snoozeMinutes: Int,
    val sleepGoalMinutes: Int,
    val recordSounds: Boolean,
    val sensitivity: Sensitivity,
    val motionSensing: Boolean,
    val vibrate: Boolean,
    val rampSeconds: Int,
    val puzzleDigits: Int,
    val puzzleCount: Int,
    val alarmSoundUri: String,
    val alarmSoundLabel: String,
    val mutedLabels: Set<String>,
    val clipRetentionDays: Int,
    val nightRetentionDays: Int
) {
    fun writeTo(settings: Settings) {
        settings.alarmEnabled = alarmEnabled
        settings.alarmHour = alarmHour
        settings.alarmMinute = alarmMinute
        settings.windowMinutes = windowMinutes
        settings.snoozeMinutes = snoozeMinutes
        settings.sleepGoalMinutes = sleepGoalMinutes
        settings.recordSounds = recordSounds
        settings.sensitivity = sensitivity
        settings.motionSensing = motionSensing
        settings.vibrate = vibrate
        settings.rampSeconds = rampSeconds
        settings.puzzleDigits = puzzleDigits
        settings.puzzleCount = puzzleCount
        settings.alarmSoundUri = alarmSoundUri
        settings.alarmSoundLabel = alarmSoundLabel
        settings.mutedLabels = mutedLabels
        settings.clipRetentionDays = clipRetentionDays
        settings.nightRetentionDays = nightRetentionDays
    }
}
