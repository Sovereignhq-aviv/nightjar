package org.sovereignhq.nightjar.data

import kotlinx.serialization.Serializable

/**
 * Sleep stages.
 *
 * REM is appended rather than inserted in physiological order on purpose: the ordinal is what gets
 * written into saved sessions, so putting REM between AWAKE and LIGHT would silently turn every
 * previously recorded light minute into deep sleep. Drawing order lives in the hypnogram instead.
 */
enum class Stage { AWAKE, LIGHT, DEEP, REM }

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
        fun from(name: String): EventKind = entries.firstOrNull { it.name == name } ?: OTHER
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
    val snoring: Boolean = false,
    /** Breaths per minute measured from the low-frequency envelope. 0 when nothing was detectable. */
    val breathRate: Float = 0f,
    /**
     * How steady the breathing was, 0..1. High means metronomic, which is deep sleep; low with
     * little movement is the signature of REM.
     */
    val breathRegularity: Float = 0f
)

/** A recorded clip sitting in the app's private clips/ folder. */
@Serializable
data class SoundClip(
    val fileName: String,
    val startedAtMs: Long,
    val durationMs: Long,
    /** [EventKind] name, as decided by the classifier or the fallback heuristic. */
    val kind: String,
    /** Peak loudness, in dB above the room's background level. */
    val peakDb: Float,
    val envelope: List<Int> = emptyList(),
    /** Starred clips are never auto-deleted. */
    val starred: Boolean = false,
    /**
     * The trained model's own label, far more specific than [kind] - "Fart", "Cough", "Snoring",
     * "Speech". Empty when the model was unavailable and the heuristic decided alone.
     */
    val detail: String = "",
    /** The model's confidence in [detail], 0..1. */
    val confidence: Float = 0f,
    /**
     * Set when the listener corrected the label. Overrides [kind] everywhere, and is the training
     * data for a personalised model, which is why corrected clips are starred automatically.
     */
    val userLabel: String? = null
) {
    /** What to show and filter by: the human's correction wins over any machine's guess. */
    val eventKind: EventKind get() = EventKind.from(userLabel ?: kind)

    val wasCorrected: Boolean get() = userLabel != null
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
    val ratingStars: Int = 0,
    /**
     * What the hardware actually did: which microphone input was granted, how many frames arrived,
     * the quietest level heard. Shown when a night comes back empty, because "no recordings" has
     * several very different causes and they are indistinguishable without this.
     */
    val diagnostics: String = ""
) {
    fun loudestClips(limit: Int): List<SoundClip> =
        clips.sortedByDescending { it.peakDb }.take(limit)
}

/** Everything derived from a session. Computed on read, never stored, so tuning changes apply retroactively. */
data class SessionStats(
    val totalMinutes: Int,
    val asleepMinutes: Int,
    val deepMinutes: Int,
    val lightMinutes: Int,
    val remMinutes: Int,
    val awakeMinutes: Int,
    val awakenings: Int,
    val sleepOnsetMinutes: Int,
    val efficiencyPct: Int,
    val score: Int,
    val snoreMinutes: Int,
    /** Average breaths per minute across the minutes where breathing was detectable. */
    val breathRate: Float
) {
    val deepFraction: Float get() = if (asleepMinutes > 0) deepMinutes.toFloat() / asleepMinutes else 0f
}

val ALL_TAGS = listOf("Caffeine", "Alcohol", "Late meal", "Workout", "Stress", "Screen time")
