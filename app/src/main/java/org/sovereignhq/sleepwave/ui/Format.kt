package org.sovereignhq.sleepwave.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val clock = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dayMonth = SimpleDateFormat("EEE d MMM", Locale.getDefault())
private val weekday = SimpleDateFormat("EEE", Locale.getDefault())

fun formatClock(ms: Long): String = clock.format(Date(ms))

fun formatDay(ms: Long): String = dayMonth.format(Date(ms))

fun formatWeekday(ms: Long): String = weekday.format(Date(ms))

/** 432 -> "7h 12m", 45 -> "45m". */
fun formatDuration(minutes: Int): String {
    if (minutes <= 0) return "0m"
    val h = minutes / 60
    val m = minutes % 60
    return if (h == 0) "${m}m" else if (m == 0) "${h}h" else "${h}h ${m}m"
}

fun formatHourMinute(hour: Int, minute: Int): String =
    "%02d:%02d".format(hour, minute)
