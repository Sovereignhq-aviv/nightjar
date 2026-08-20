package org.sovereignhq.nightjar.ui

import org.sovereignhq.nightjar.data.EventKind
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val clock = SimpleDateFormat("HH:mm", Locale.getDefault())
private val clockSeconds = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
private val dayMonth = SimpleDateFormat("EEE d MMM", Locale.getDefault())
private val shortDay = SimpleDateFormat("d MMM", Locale.getDefault())
private val weekday = SimpleDateFormat("EEE", Locale.getDefault())

fun formatClock(ms: Long): String = clock.format(Date(ms))

fun formatClockSeconds(ms: Long): String = clockSeconds.format(Date(ms))

fun formatDay(ms: Long): String = dayMonth.format(Date(ms))

fun formatShortDay(ms: Long): String = shortDay.format(Date(ms))

fun formatWeekday(ms: Long): String = weekday.format(Date(ms))

/** 432 -> "7h 12m", 45 -> "45m". */
fun formatDuration(minutes: Int): String {
    if (minutes <= 0) return "0m"
    val h = minutes / 60
    val m = minutes % 60
    return if (h == 0) "${m}m" else if (m == 0) "${h}h" else "${h}h ${m}m"
}

fun formatHourMinute(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

fun eventLabel(kind: EventKind): String = when (kind) {
    EventKind.SNORE -> "Snoring"
    EventKind.VOICE -> "Talking"
    EventKind.RUMBLE -> "Rumble"
    EventKind.THUMP -> "Thump"
    EventKind.OTHER -> "Noise"
}

/** Plural label for filter chips and counts. */
fun eventLabelPlural(kind: EventKind): String = when (kind) {
    EventKind.SNORE -> "Snoring"
    EventKind.VOICE -> "Talking"
    EventKind.RUMBLE -> "Rumbles"
    EventKind.THUMP -> "Thumps"
    EventKind.OTHER -> "Other"
}

fun eventDescription(kind: EventKind): String = when (kind) {
    EventKind.SNORE -> "Low and rhythmic, on a breathing cadence"
    EventKind.VOICE -> "Speech-shaped: talking, mumbling, shouting"
    EventKind.RUMBLE -> "Short low burst with no rhythm behind it"
    EventKind.THUMP -> "A sharp hit: rolling over, the headboard, something dropped"
    EventKind.OTHER -> "Loud enough to record, not confidently anything else"
}
