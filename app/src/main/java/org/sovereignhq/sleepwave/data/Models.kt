package org.sovereignhq.sleepwave.data

import kotlinx.serialization.Serializable

/**
 * Three-way sleep staging. We deliberately do NOT claim to separate REM from light sleep:
 * telling them apart needs heart-rate or EEG data that a phone on a nightstand does not have.
 * REM shows up inside LIGHT.
 */
enum class Stage { AWAKE, LIGHT, DEEP }

/** One minute of the night. */
@Serializable
data class Sample(
    val minute: Int,
    /** 0..1 combined restlessness from room sound + phone movement. */
    val activity: Float,
    /** Peak loudness this minute, in dB above the room's own noise floor. */
    val loudnessDb: Float,
    /** [Stage] ordinal, assigned once the whole night is known. */
    val stage: Int = Stage.LIGHT.ordinal,
    /** True when a snore burst was confirmed inside this minute. */
    val snoring: Boolean = false
)

/** A recorded audio clip sitting in the app's private clips/ folder. */
@Serializable
data class SoundClip(
    val fileName: String,
    val startedAtMs: Long,
    val durationMs: Long,
    /** "SNORE" or "NOISE". */
    val kind: String,
    val peakDb: Float
)

@Serializable
data class SleepSession(
    val id: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val alarmTargetMs: Long,
    val windowMinutes: Int,
    /** When the alarm actually rang, if it did. */
    val wokeAtMs: Long? = null,
    /** True when the smart alarm found a light-sleep moment instead of hitting the hard deadline. */
    val wokeSmart: Boolean = false,
    val samples: List<Sample> = emptyList(),
    val clips: List<SoundClip> = emptyList(),
    val snoreMinutes: Int = 0,
    val tags: List<String> = emptyList(),
    val note: String = "",
    /** How the user says they feel, 0 = unrated, 1..5 stars. */
    val ratingStars: Int = 0
)

/** Everything derived from a session. Computed on read, never stored, so tuning changes apply retroactively. */
data class SessionStats(
    val totalMinutes: Int,
    val asleepMinutes: Int,
    val deepMinutes: Int,
    val lightMinutes: Int,
    val awakeMinutes: Int,
    val awakenings: Int,
    val sleepOnsetMinutes: Int,
    val efficiencyPct: Int,
    val score: Int,
    val snoreMinutes: Int
) {
    val deepFraction: Float get() = if (asleepMinutes > 0) deepMinutes.toFloat() / asleepMinutes else 0f
}

val ALL_TAGS = listOf("Caffeine", "Alcohol", "Late meal", "Workout", "Stress", "Screen time")
