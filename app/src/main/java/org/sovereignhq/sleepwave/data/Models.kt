package org.sovereignhq.sleepwave.data

import kotlinx.serialization.Serializable

/**
 * Three-way sleep staging. We deliberately do NOT claim to separate REM from light sleep:
 * telling them apart needs heart-rate or EEG data that a phone on a nightstand does not have.
 * REM shows up inside LIGHT.
 */
enum class Stage { AWAKE, LIGHT, DEEP }

/**
 * What kind of noise a recording turned out to be.
 *
 * The classes exist because "you made 94 noises last night" is useless, while "11 snoring
 * stretches, 3 times you talked, 6 rumbles" is something you can actually navigate.
 */
enum class EventKind {
    /** Low-frequency, repeating on a breathing cadence. */
    SNORE,

    /** Mid-band and modulated: talking, mumbling, shouting. */
    VOICE,

    /** Short low-frequency burst with no cadence. Gut noises, and yes, farts. */
    RUMBLE,

    /** Sharp broadband hit: rolling over, knocking the headboard, dropping something. */
    THUMP,

    /** Loud enough to record, not confidently anything else. */
    OTHER;

    companion object {
        fun from(name: String): EventKind =
            entries.firstOrNull { it.name == name } ?: OTHER
    }
}

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

/**
 * A recorded clip sitting in the app's private clips/ folder.
 *
 * [envelope] is computed once when the WAV is written: 0-100 loudness buckets across the clip.
 * Storing it means the list can draw a real waveform for 120 rows without opening a single audio
 * file, which is the difference between a list that scrolls and one that stutters.
 */
@Serializable
data class SoundClip(
    val fileName: String,
    val startedAtMs: Long,
    val durationMs: Long,
    /** [EventKind] name. */
    val kind: String,
    /** Peak loudness, in dB above the room's background level. */
    val peakDb: Float,
    val envelope: List<Int> = emptyList(),
    /** Starred clips are never auto-deleted. */
    val starred: Boolean = false
) {
    val eventKind: EventKind get() = EventKind.from(kind)
}

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
) {
    /** Clips whose audio file has been pruned are kept out of the library. */
    fun clipsNewestFirst(): List<SoundClip> = clips.sortedByDescending { it.startedAtMs }

    fun loudestClips(limit: Int): List<SoundClip> =
        clips.sortedByDescending { it.peakDb }.take(limit)
}

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
