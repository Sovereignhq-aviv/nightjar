package org.sovereignhq.sleepwave.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.sovereignhq.sleepwave.data.SessionStats
import org.sovereignhq.sleepwave.data.SleepSession
import org.sovereignhq.sleepwave.ui.components.Bar
import org.sovereignhq.sleepwave.ui.components.EmptyState
import org.sovereignhq.sleepwave.ui.components.NightCard
import org.sovereignhq.sleepwave.ui.components.SectionLabel
import org.sovereignhq.sleepwave.ui.components.StatTile
import org.sovereignhq.sleepwave.ui.components.TrendBars
import org.sovereignhq.sleepwave.ui.theme.DataColors
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun TrendsScreen(
    vm: SleepViewModel,
    onOpenNight: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var nights by remember { mutableIntStateOf(7) }

    val all = vm.sessions
    val window = all.take(nights).reversed()   // oldest first, so charts read left to right
    val paired = window.map { it to vm.stats(it) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Text("Trends", style = MaterialTheme.typography.headlineMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 14, 30).forEach { n ->
                PeriodChip("$n nights", nights == n, { nights = n }, Modifier.weight(1f))
            }
        }

        if (all.isEmpty()) {
            EmptyState(
                title = "Nothing to compare yet",
                body = "Track two or three nights and this page starts showing what actually moves " +
                    "your sleep."
            )
            Spacer(Modifier.height(24.dp))
            return@Column
        }

        SectionLabel("Averages over ${paired.size} night${if (paired.size == 1) "" else "s"}")
        NightCard {
            Row(Modifier.fillMaxWidth()) {
                StatTile(
                    "asleep",
                    formatDuration(paired.map { it.second.asleepMinutes }.average().roundToInt()),
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    "quality",
                    "${paired.map { it.second.score }.average().roundToInt()}%",
                    MaterialTheme.colorScheme.secondary,
                    Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth()) {
                StatTile(
                    "bedtime",
                    averageBedtime(window.map { it.startedAtMs }),
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    "wake-up",
                    averageWakeTime(window.map { it.endedAtMs }),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        SectionLabel("Hours asleep")
        NightCard {
            TrendBars(
                bars = paired.map {
                    Bar(
                        label = formatWeekday(it.first.startedAtMs).take(2),
                        value = it.second.asleepMinutes / 60f,
                        highlight = it.second.asleepMinutes >= vm.settings.sleepGoalMinutes
                    )
                },
                goalValue = vm.settings.sleepGoalMinutes / 60f
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Dotted line is your ${formatDuration(vm.settings.sleepGoalMinutes)} goal. " +
                    "Solid bars hit it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionLabel("Quality")
        NightCard {
            TrendBars(
                bars = paired.map {
                    Bar(formatWeekday(it.first.startedAtMs).take(2), it.second.score.toFloat())
                },
                barColor = MaterialTheme.colorScheme.secondary
            )
        }

        if (paired.any { it.first.clips.isNotEmpty() }) {
            SectionLabel("Recordings per night")
            NightCard {
                TrendBars(
                    bars = paired.map {
                        Bar(
                            formatWeekday(it.first.startedAtMs).take(2),
                            it.first.clips.size.toFloat()
                        )
                    },
                    barColor = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${paired.sumOf { it.first.clips.size }} clips across this period, " +
                        "${paired.sumOf { p -> p.first.clips.count { it.starred } }} saved.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        WeekdayBreakdown(paired)

        if (paired.any { it.second.snoreMinutes > 0 }) {
            SectionLabel("Snoring per night")
            NightCard {
                TrendBars(
                    bars = paired.map {
                        Bar(
                            formatWeekday(it.first.startedAtMs).take(2),
                            it.second.snoreMinutes.toFloat()
                        )
                    },
                    barColor = DataColors.snore
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Total ${formatDuration(paired.sumOf { it.second.snoreMinutes })} of snoring " +
                        "across this period.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        TagInfluence(all.take(nights), vm)

        SectionLabel("Every night")
        NightCard(padding = 8) {
            all.take(nights).forEach { session ->
                val stats = vm.stats(session)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenNight(session.id) }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(formatDay(session.startedAtMs), style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${formatClock(session.startedAtMs)} - ${formatClock(session.endedAtMs)}" +
                                if (session.clips.isEmpty()) "" else "  ·  ${session.clips.size} clips",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatDuration(stats.asleepMinutes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "   ${stats.score}%",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun WeekdayBreakdown(paired: List<Pair<SleepSession, SessionStats>>) {
    val byDay = paired.groupBy {
        Calendar.getInstance().apply { timeInMillis = it.first.startedAtMs }
            .get(Calendar.DAY_OF_WEEK)
    }
    if (byDay.size < 3) return

    // Monday first, which is how most people think about their week.
    val order = listOf(
        Calendar.MONDAY to "Mo", Calendar.TUESDAY to "Tu", Calendar.WEDNESDAY to "We",
        Calendar.THURSDAY to "Th", Calendar.FRIDAY to "Fr", Calendar.SATURDAY to "Sa",
        Calendar.SUNDAY to "Su"
    )

    SectionLabel("Quality by day of week")
    NightCard {
        TrendBars(
            bars = order.map { (day, label) ->
                val scores = byDay[day]?.map { it.second.score } ?: emptyList()
                Bar(label, if (scores.isEmpty()) 0f else scores.average().toFloat())
            }
        )
        val best = order.mapNotNull { (day, label) ->
            val nights = byDay[day]
            if (nights.isNullOrEmpty()) null else label to nights.map { it.second.score }.average()
        }.maxByOrNull { it.second }

        if (best != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Your best nights land on ${fullDayName(best.first)}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Compares nights where a tag was set against nights where it was not. Only shown once there is
 * enough on both sides to be worth reading - two nights either way is not a pattern.
 */
@Composable
private fun TagInfluence(sessions: List<SleepSession>, vm: SleepViewModel) {
    val tags = sessions.flatMap { it.tags }.distinct()
    val rows = tags.mapNotNull { tag ->
        val with = sessions.filter { tag in it.tags }
        val without = sessions.filter { tag !in it.tags }
        if (with.size < 2 || without.size < 2) return@mapNotNull null
        val withScore = with.map { vm.stats(it).score }.average()
        val withoutScore = without.map { vm.stats(it).score }.average()
        Triple(tag, (withScore - withoutScore).roundToInt(), with.size)
    }.sortedByDescending { abs(it.second) }

    if (rows.isEmpty()) return

    SectionLabel("What changes your sleep")
    NightCard {
        rows.forEach { (tag, delta, count) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(tag, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "$count night${if (count == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = if (delta >= 0) "+$delta%" else "$delta%",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (delta >= 0) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Difference in quality score on nights you tagged this, against nights you did not. " +
                "Suggestive, not proof - a handful of nights is a small sample.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PeriodChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Bedtimes straddle midnight, so a plain average of "minutes past midnight" would put 23:30 and
 * 00:30 at lunchtime. Late-evening times are shifted past the end of the day before averaging.
 */
private fun averageBedtime(times: List<Long>): String {
    if (times.isEmpty()) return "-"
    val shifted = times.map { ms ->
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        val minutes = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        if (minutes < 12 * 60) minutes + 24 * 60 else minutes
    }
    val mean = shifted.average().roundToInt() % (24 * 60)
    return formatHourMinute(mean / 60, mean % 60)
}

private fun averageWakeTime(times: List<Long>): String {
    if (times.isEmpty()) return "-"
    val minutes = times.map { ms ->
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }
    val mean = minutes.average().roundToInt() % (24 * 60)
    return formatHourMinute(mean / 60, mean % 60)
}

private fun fullDayName(short: String): String = when (short) {
    "Mo" -> "Mondays"
    "Tu" -> "Tuesdays"
    "We" -> "Wednesdays"
    "Th" -> "Thursdays"
    "Fr" -> "Fridays"
    "Sa" -> "Saturdays"
    else -> "Sundays"
}
