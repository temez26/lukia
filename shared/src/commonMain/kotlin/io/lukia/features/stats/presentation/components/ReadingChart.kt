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
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lukia.core.infrastructure.MonthForm
import io.lukia.core.infrastructure.monthName
import io.lukia.features.stats.domain.DayReading
import io.lukia.features.stats.domain.MonthReading
import io.lukia.features.stats.presentation.dayLabel
import io.lukia.features.stats.presentation.dayName
import io.lukia.features.stats.presentation.readingTime
import io.lukia.resources.Res
import io.lukia.resources.stats_day_spoken
import io.lukia.resources.stats_still_to_come
import io.lukia.shared.presentation.theme.LocalMotionStyle
import io.lukia.shared.presentation.theme.LukiaAlpha
import io.lukia.shared.presentation.theme.Space
import io.lukia.shared.presentation.theme.afterScreenChange
import org.jetbrains.compose.resources.stringResource

/** How tall the tallest bar stands. Enough to read a shape from, not a wall. */
private val ChartHeight: Dp = 104.dp

/** How wide one day's bar is drawn. */
private val BarWidth: Dp = 10.dp

/** The smallest a bar's label shrinks to before it is ellipsised instead. */
private val MinLabelSize: TextUnit = 8.sp

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
 * One column of [ReadingChart]: a day of a week, or a month of a year.
 *
 * [spoken] is the whole column as one sentence, so running along the chart with
 * a screen reader gives one readable fact per bar instead of two fragments.
 */
internal data class ChartBar(
    val label: String,
    val spoken: String,
    val durationMs: Long,
    /** Today, or the month today is in: marked on the label. */
    val isMarked: Boolean = false,
    val isFuture: Boolean = false,
) {
    val hasReading: Boolean get() = durationMs > 0
}

/**
 * Reading as bars: a week's seven days, or a year's twelve months.
 *
 * A chart rather than a row of numbers because the question is a shape: every
 * day or all at the weekend, this month against the last. Numbers in a row are
 * compared by hand; bars are compared by looking.
 *
 * Scaled to the busiest bar shown rather than a fixed number of hours: there is
 * no normal amount of reading, and a chart scaled to somebody else's hour would
 * show most people a row of stubs.
 *
 * Each bar takes an equal share of the width rather than only the room its own
 * label needs, so the columns stay on a grid whatever the labels say.
 */
@Composable
internal fun ReadingChart(
    bars: List<ChartBar>,
    modifier: Modifier = Modifier,
) {
    if (bars.isEmpty()) return

    val growth = chartGrowth()
    val busiest = bars.maxOf { it.durationMs }

    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = modifier.fillMaxWidth(),
    ) {
        bars.forEach { bar ->
            BarColumn(
                bar = bar,
                share = if (busiest > 0) bar.durationMs.toFloat() / busiest else 0f,
                growth = growth,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A week's days as bars, today marked. */
@Composable
internal fun dayBars(days: List<DayReading>): List<ChartBar> = days.map { day ->
    ChartBar(
        label = dayLabel(day.date),
        spoken = stringResource(Res.string.stats_day_spoken, dayName(day.date), day.spoken()),
        durationMs = day.durationMs,
        isMarked = day.isToday,
        isFuture = day.isFuture,
    )
}

/** A year's months as bars, the current month marked. */
@Composable
internal fun monthBars(months: List<MonthReading>): List<ChartBar> = months.map { month ->
    ChartBar(
        label = monthName(month.month, MonthForm.Short).take(1),
        spoken = stringResource(
            Res.string.stats_day_spoken,
            monthName(month.month, MonthForm.Full),
            if (month.isFuture) stringResource(Res.string.stats_still_to_come) else readingTime(month.durationMs),
        ),
        durationMs = month.durationMs,
        isMarked = month.isCurrent,
        isFuture = month.isFuture,
    )
}

/**
 * How much of the chart has grown, from nothing to its full height.
 *
 * One animation for the seven bars rather than one apiece: they are the same
 * movement at the same moment, so seven clocks can only disagree - and a bar
 * grows by being measured taller, so seven of them were seven layout passes a
 * frame for one picture.
 *
 * It waits out the screen change - see [afterScreenChange] - because this chart
 * arrives on a screen still sliding in, and a chart growing through a push
 * spends the push's frames twice. A reader who asked the app to stop flourishing
 * gets the finished chart and no growth at all.
 */
@Composable
private fun chartGrowth(): Float {
    val motion = LocalMotionStyle.current
    var grown by remember(motion) { mutableStateOf(!motion.flourishes) }

    LaunchedEffect(motion) { grown = true }

    val growth by animateFloatAsState(
        targetValue = if (grown) 1f else 0f,
        animationSpec = motion.afterScreenChange(),
        label = "readingChart",
    )

    return growth
}

/** One bar and the name it is known by, announced as one sentence. */
@Composable
private fun BarColumn(
    bar: ChartBar,
    share: Float,
    growth: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clearAndSetSemantics { contentDescription = bar.spoken },
    ) {
        Bar(bar = bar, share = share, growth = growth)

        BarLabel(bar = bar, modifier = Modifier.padding(top = Space.lg))
    }
}

/** The bar itself, growing out of the track it is drawn in. */
@Composable
private fun Bar(
    bar: ChartBar,
    share: Float,
    growth: Float,
    modifier: Modifier = Modifier,
) {
    val height = if (bar.hasReading) share.coerceIn(MinimumBar, 1f) * growth else 0f

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
                        alpha = LukiaAlpha.Hover * if (bar.isFuture) FutureTrackShare else 1f,
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
 * The name under a bar, and the pill around today's, or this month's.
 *
 * Today is marked on the label rather than by colouring its bar, so the bars
 * stay comparable with each other - a bar in a second colour is read as a second
 * quantity - and the reader can still find where in the week they are at a
 * glance.
 */
@Composable
private fun BarLabel(
    bar: ChartBar,
    modifier: Modifier = Modifier,
) {
    val marked = bar.isMarked
    val style = MaterialTheme.typography.labelSmall

    Text(
        text = bar.label,
        style = style,
        // Shrunk to fit before it is cut. A month's column is a twelfth of a
        // phone wide, and at the largest type even one letter and the pill's
        // padding outgrow it; a label cut to "…" names nothing.
        autoSize = TextAutoSize.StepBased(minFontSize = MinLabelSize, maxFontSize = style.fontSize),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        fontWeight = if (marked || bar.hasReading) FontWeight.SemiBold else FontWeight.Normal,
        color = when {
            marked -> MaterialTheme.colorScheme.onPrimary
            bar.isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = LukiaAlpha.Disabled)
            bar.hasReading -> MaterialTheme.colorScheme.onSurface
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
@Composable
private fun DayReading.spoken(): String = when {
    isFuture -> stringResource(Res.string.stats_still_to_come)
    else -> readingTime(durationMs)
}
