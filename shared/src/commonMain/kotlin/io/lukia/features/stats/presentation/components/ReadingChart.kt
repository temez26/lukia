package io.lukia.features.stats.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.stats.domain.DayReading
import io.lukia.features.stats.presentation.dayLabel
import io.lukia.features.stats.presentation.dayName
import io.lukia.features.stats.presentation.readingTime
import io.lukia.shared.presentation.theme.LukiaAlpha
import io.lukia.shared.presentation.theme.LukiaMotion
import io.lukia.shared.presentation.theme.Space

/** How tall the tallest bar stands. Enough to read a shape from, not a wall. */
private val ChartHeight: Dp = 104.dp

/** How wide one day's bar is drawn. */
private val BarWidth: Dp = 10.dp

/** The capsule every bar and every track is cut to, at both ends. */
private val BarShape: Shape = RoundedCornerShape(percent = 50)

/**
 * The least of the chart a day with any reading at all shows.
 *
 * Without it, ten minutes beside a four-hour Sunday is a bar under a pixel tall -
 * which reads as a day with nothing on it, and is the opposite of what the chart
 * is for. At this size the capsule shows as a dot: something happened, and not
 * much.
 */
private const val MinimumBar = 0.07f

/** How faint the track behind a day still to come is drawn, against a day past. */
private const val FutureTrackShare = 0.45f

/**
 * The week, Monday to Sunday, as seven bars.
 *
 * A chart rather than seven numbers because the question it answers is a shape:
 * whether the reading is every day or all at the weekend, whether this week is
 * like the last one. Numbers in a row are read one at a time and compared by
 * hand; bars are compared by looking.
 *
 * Scaled to the reader's own busiest day rather than to a fixed number of hours.
 * There is no such thing as a normal amount of reading, and a chart scaled to
 * somebody else's hour would show most people a row of stubs.
 *
 * Each day takes an equal share of the width rather than only the room its own
 * label needs, so the columns stay on a grid whatever the labels say and the
 * bars line up with the days under them.
 */
@Composable
internal fun ReadingChart(
    days: List<DayReading>,
    busiestMs: Long,
    modifier: Modifier = Modifier,
) {
    if (days.isEmpty()) return

    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = modifier.fillMaxWidth(),
    ) {
        days.forEach { day ->
            DayColumn(
                day = day,
                share = if (busiestMs > 0) day.durationMs.toFloat() / busiestMs else 0f,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * One day: its bar, and the name it is known by.
 *
 * Announced as a sentence rather than as a bar and a label, so that running down
 * the week with a screen reader gives seven readable facts instead of fourteen
 * fragments.
 */
@Composable
private fun DayColumn(
    day: DayReading,
    share: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "${dayName(day.date)}: ${day.spoken()}"
        },
    ) {
        DayBar(day = day, share = share)

        DayLabel(day = day, modifier = Modifier.padding(top = Space.lg))
    }
}

/** The bar itself, growing out of the track it is drawn in. */
@Composable
private fun DayBar(
    day: DayReading,
    share: Float,
    modifier: Modifier = Modifier,
) {
    val height by animateFloatAsState(
        targetValue = if (day.hasReading) share.coerceIn(MinimumBar, 1f) else 0f,
        animationSpec = LukiaMotion.gentle(),
        label = "readingBar",
    )

    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = modifier.height(ChartHeight).width(BarWidth),
    ) {
        // The track is drawn on every day, including the empty ones: seven faint
        // columns say "this is a week" where three floating bars say only "here
        // are three numbers". A day still to come is fainter again, so the week
        // reads as partly written rather than as mostly unread.
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .clip(BarShape)
                .background(
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = LukiaAlpha.Hover * if (day.isFuture) FutureTrackShare else 1f,
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxHeight(height)
                .fillMaxWidth()
                .clip(BarShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/**
 * The three letters under a bar, and the pill around today's.
 *
 * Today is marked on the label rather than by colouring its bar, so the bars
 * stay comparable with each other - a bar in a second colour is read as a second
 * quantity - and the reader can still find where in the week they are at a
 * glance.
 */
@Composable
private fun DayLabel(
    day: DayReading,
    modifier: Modifier = Modifier,
) {
    val marked = day.isToday

    Text(
        text = dayLabel(day.date),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (marked || day.hasReading) FontWeight.SemiBold else FontWeight.Normal,
        color = when {
            marked -> MaterialTheme.colorScheme.onPrimary
            day.isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = LukiaAlpha.Disabled)
            day.hasReading -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier
            .clip(BarShape)
            .then(
                if (marked) {
                    Modifier.background(MaterialTheme.colorScheme.primary)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = Space.sm, vertical = Space.xxs),
    )
}

/** What a day is worth saying out loud, which "None yet" is not on a Friday to come. */
private fun DayReading.spoken(): String = when {
    isFuture -> "still to come"
    else -> readingTime(durationMs)
}
