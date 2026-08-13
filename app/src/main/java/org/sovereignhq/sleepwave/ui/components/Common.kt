package org.sovereignhq.sleepwave.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.sovereignhq.sleepwave.ui.theme.NightBorder
import org.sovereignhq.sleepwave.ui.theme.NightSurface
import org.sovereignhq.sleepwave.ui.theme.TextMuted

@Composable
fun NightCard(
    modifier: Modifier = Modifier,
    padding: Int = 20,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NightSurface)
            .padding(padding.dp),
        content = content
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = TextMuted,
        modifier = modifier
    )
}

@Composable
fun StatTile(label: String, value: String, tint: Color? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = tint ?: MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
    }
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = "  $label",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NightSurface)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun HairLine(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(NightBorder)
    )
}
