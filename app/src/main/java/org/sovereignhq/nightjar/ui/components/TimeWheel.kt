package org.sovereignhq.nightjar.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The alarm time as three scrolling wheels: hour, minute, AM/PM.
 *
 * This replaced a circular dial. The dial photographed better and was worse in the hand: a ring maps
 * twelve hours onto 360 degrees, so a thumb covers several minutes at once and landing on an exact
 * time meant nudging at it. Wheels give every value its own row, land on it exactly, and take a flick
 * instead of a careful arc - which is what a control used in the dark, one-handed, half asleep needs
 * to be.
 *
 * Minute resolution is one minute rather than five. Five would be fewer rows to scroll past, and it
 * would miss the point: the complaint was not being able to land on the number you actually wanted.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimeWheel(
    hour: Int,
    minute: Int,
    enabled: Boolean,
    onChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
    rowHeight: Int = 54
) {
    // Midnight and noon read as 12 on a twelve-hour clock, so the wheel holds 12, 1, 2 ... 11.
    val hourLabels = remember { (0 until 12).map { if (it == 0) "12" else it.toString() } }
    val minuteLabels = remember { (0 until 60).map { "%02d".format(it) } }
    val halfLabels = remember { listOf("AM", "PM") }

    val hourIndex = hour % 12
    val halfIndex = if (hour >= 12) 1 else 0

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {

            // The selection band sits behind the wheels, so the centre row reads as "the value"
            // without having to style the moving text differently.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(rowHeight.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Wheel(
                    labels = hourLabels,
                    selectedIndex = hourIndex,
                    enabled = enabled,
                    rowHeight = rowHeight,
                    width = 76,
                    align = TextAlign.End,
                    onSelected = { picked -> onChange(halfIndex * 12 + picked, minute) }
                )
                Text(
                    text = ":",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
                Wheel(
                    labels = minuteLabels,
                    selectedIndex = minute.coerceIn(0, 59),
                    enabled = enabled,
                    rowHeight = rowHeight,
                    width = 76,
                    align = TextAlign.Start,
                    onSelected = { picked -> onChange(hour, picked) }
                )
                Wheel(
                    labels = halfLabels,
                    selectedIndex = halfIndex,
                    enabled = enabled,
                    rowHeight = rowHeight,
                    width = 64,
                    align = TextAlign.Center,
                    small = true,
                    onSelected = { picked -> onChange(picked * 12 + hour % 12, minute) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Wheel(
    labels: List<String>,
    selectedIndex: Int,
    enabled: Boolean,
    rowHeight: Int,
    width: Int,
    align: TextAlign,
    onSelected: (Int) -> Unit,
    small: Boolean = false
) {
    val state = rememberLazyListState()
    val rowPx = with(LocalDensity.current) { rowHeight.dp.toPx() }

    // Whatever ends up nearest the middle is the value. Read from scroll position rather than from a
    // tap, so a flick that coasts to a stop still commits.
    val centred by remember(labels.size) {
        derivedStateOf {
            val drift = if (rowPx > 0f) state.firstVisibleItemScrollOffset / rowPx else 0f
            (state.firstVisibleItemIndex + drift.roundToInt()).coerceIn(0, labels.lastIndex)
        }
    }

    LaunchedEffect(Unit) { state.scrollToItem(selectedIndex) }

    LaunchedEffect(centred, state.isScrollInProgress) {
        if (!state.isScrollInProgress && centred != selectedIndex) onSelected(centred)
    }

    LazyColumn(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(lazyListState = state),
        userScrollEnabled = enabled,
        // One blank row above and below, so the first and last values can reach the centre.
        contentPadding = PaddingValues(vertical = rowHeight.dp),
        modifier = Modifier
            .width(width.dp)
            .height((rowHeight * 3).dp)
    ) {
        itemsIndexed(labels) { index, label ->
            // Rows fade with distance from the centre, which is what makes a flat list read as a
            // wheel without any 3D trickery.
            val fade = when (abs(index - centred)) {
                0 -> 1f
                1 -> 0.38f
                else -> 0.16f
            }
            Box(
                modifier = Modifier
                    .height(rowHeight.dp)
                    .width(width.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    textAlign = align,
                    style = if (small) {
                        MaterialTheme.typography.headlineSmall
                    } else {
                        MaterialTheme.typography.displayMedium
                    },
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(fade)
                )
            }
        }
    }
}
