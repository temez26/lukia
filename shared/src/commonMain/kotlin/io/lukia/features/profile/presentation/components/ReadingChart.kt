package io.lukia.features.profile.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.profile.domain.DayReading
import io.lukia.features.profile.presentation.dayInitial
import io.lukia.features.profile.presentation.readingTime
import io.lukia.shared.presentation.theme.LukiaAlpha
import io.lukia.shared.presentation.theme.LukiaMotion
import io.lukia.shared.presentation.theme.Space

/** How tall the tallest bar stands. Enough to read a shape from, not a wall. */
private val ChartHeight: Dp = 96.dp

/** How wide one day's bar is drawn. */
private val BarWidth: Dp = 12.dp

/**
 * The least of the chart a day with any reading at all shows.
 *
 * Without it, ten minutes beside a four-hour Sunday is a bar under a pixel tall -
 * which reads as a day with nothing on it, and is the opposite of what the chart
 * is for. A stub is the honest answer: something happened, and not much.
 */
private const val MinimumBar = 0.06f

/**
 * The week, as seven bars.
 *
 * A chart rather than seven numbers because the question it answers is a shape:
 * whether the reading is every day or all at the weekend, whether this week is
 * like the last one. Numbers in a row are read one at a time and compared by
 * hand; bars are compared by looking.
 *
 * Scaled to the reader's own busiest day rather than to a fixed number of hours.
 * There is no such thing as a normal amount of reading, and a chart scaled to
 * somebody else's hour would show most people a row of stubs.
 */
@Composable
internal fun ReadingChart(
    days: List<DayReading>,
    busiestMs: Long,
    modifier: Modifier = Modifier,
) {
    if (days.isEmpty()) return

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
        modifier = modifier.fillMaxWidth(),
    ) {
        days.forEach { day ->
            DayColumn(
                day = day,
                share = if (busiestMs > 0) day.durationMs.toFloat() / busiestMs else 0f,
            )
        }
    }
}

/**
 * One day: its bar, and the letter it is known by.
 *
 * Announced as a sentence rather than as a bar and a letter, so that running
 * down the week with a screen reader gives seven readable facts instead of
 * fourteen fragments.
 */
@Composable
private fun DayColumn(
    day: DayReading,
    share: Float,
    modifier: Modifier = Modifier,
) {
    val height by animateFloatAsState(
        targetValue = when {
            !day.hasReading -> 0f
            else -> share.coerceIn(MinimumBar, 1f)
        },
        animationSpec = LukiaMotion.gentle(),
        label = "readingBar",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "${day.date.dayOfWeek.name.lowercase()}: ${readingTime(day.durationMs)}"
        },
    ) {
        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier.height(ChartHeight).width(BarWidth),
        ) {
            // The track is drawn on every day, including the empty ones: seven
            // faint columns say "this is a week" where three floating bars say
            // only "here are three numbers".
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant
                            .copy(alpha = LukiaAlpha.Hover),
                    ),
            )

            Box(
                modifier = Modifier
                    .fillMaxHeight(height)
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }

        Text(
            text = dayInitial(day.date),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (day.hasReading) FontWeight.SemiBold else FontWeight.Normal,
            color = if (day.hasReading) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(top = Space.md),
        )
    }
}
