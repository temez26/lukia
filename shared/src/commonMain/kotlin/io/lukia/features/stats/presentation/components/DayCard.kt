package io.lukia.features.stats.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lukia.core.infrastructure.MonthForm
import io.lukia.core.infrastructure.monthName
import io.lukia.features.stats.domain.DayReading
import io.lukia.features.stats.presentation.dayName
import io.lukia.features.stats.presentation.readingTime
import io.lukia.features.stats.presentation.streakLine
import io.lukia.resources.Res
import io.lukia.resources.stats_about_average
import io.lukia.resources.stats_day_spoken
import io.lukia.resources.stats_less_than_average
import io.lukia.resources.stats_more_than_average
import io.lukia.resources.stats_selected_day
import io.lukia.resources.stats_still_to_come
import io.lukia.resources.stats_time_dash
import io.lukia.shared.presentation.components.icons.CalendarGlyph
import io.lukia.shared.presentation.components.icons.FlameGlyph
import io.lukia.shared.presentation.theme.LocalMotionStyle
import io.lukia.shared.presentation.theme.LukiaAlpha
import io.lukia.shared.presentation.theme.Space
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import kotlin.math.absoluteValue

/** Digits of equal width, so a figure rolling from 1 to 8 doesn't shuffle its neighbours. */
private const val TabularFigures = "tnum"

/** How tall the bar under the day's time is drawn. */
private val DayBarHeight: Dp = 6.dp

private val CardGlyph: Dp = 16.dp

/**
 * [size] grown with the reader's text size, so an icon set beside a word stays
 * in proportion to it at the largest type rather than shrinking beside it.
 */
@Composable
internal fun textSized(size: Dp): Dp = with(LocalDensity.current) { size.value.sp.toDp() }

/** Within this much of the average, a day is "about" average rather than above or below it. */
private const val AboutAverageMs = 60_000L

/**
 * The selected day, set as the one thing in the column worth reading first: its
 * date, exactly how long was read on it, the run it belongs to, how it stands
 * against the period's busiest day, and against an average one.
 *
 * In one place beside the calendar rather than in a bubble over the day tapped,
 * so it never covers a neighbour or runs off the card at the largest type, and
 * the eye learns where to look once for a month and a year alike.
 *
 * On a tint of the accent, so the column has a focal point and the figures
 * under it read as context for this day rather than as more of the same.
 */
@Composable
internal fun DayCard(
    day: DayReading,
    busiestMs: Long,
    averageMs: Long,
    streak: Int,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val date = stringResource(
        Res.string.stats_selected_day,
        dayName(day.date),
        day.date.day,
        // The short form, because it is the one every language can put after a
        // day number without changing its case.
        monthName(day.date.month, MonthForm.Short),
    )
    val time = if (day.isFuture) stringResource(Res.string.stats_time_dash) else readingTime(day.durationMs)
    val spoken = stringResource(
        Res.string.stats_day_spoken,
        date,
        if (day.isFuture) stringResource(Res.string.stats_still_to_come) else time,
    )
    val forward = rememberForward(day.date)
    val motion = LocalMotionStyle.current
    val share = if (day.isFuture || busiestMs <= 0) 0f else (day.durationMs.toFloat() / busiestMs).coerceIn(0f, 1f)
    val bar by animateFloatAsState(share, motion.settle(), label = "dayBar")

    Column(
        verticalArrangement = Arrangement.spacedBy(Space.sm),
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.primary.copy(alpha = LukiaAlpha.Selected))
            .padding(Space.lg),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(Space.sm),
            itemVerticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            DayTime(date = date, time = time, spoken = spoken, forward = forward)

            // Only a run is worth a chip; the absence of one needs no announcing.
            if (streak > 0) StreakChip(days = streak)
        }

        DayBar(share = bar)

        // Held as an empty line when there is nothing to compare, so the card
        // stays one height whichever day is tapped.
        val line = averageLine(day = day, averageMs = averageMs)
        Text(
            text = line.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = if (line == null) Modifier.clearAndSetSemantics { } else Modifier,
        )
    }
}

/** The date over the day's time, announced as one sentence. */
@Composable
private fun DayTime(date: String, time: String, spoken: String, forward: Boolean) {
    Column(
        modifier = Modifier
            .padding(end = Space.lg)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        DateLine(date = date, forward = forward)

        RollingText(
            text = time,
            forward = forward,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = TabularFigures,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Space.xxs),
        )
    }
}

/** The day against the period's busiest day: full is the best day shown. */
@Composable
private fun DayBar(share: Float) {
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(DayBarHeight)
            .clip(CircleShape)
            .background(colors.primary.copy(alpha = LukiaAlpha.Tonal)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(share)
                .clip(CircleShape)
                .background(colors.primary),
        )
    }
}

/** The date over the time, with a calendar beside it, sliding the way the days went. */
@Composable
private fun DateLine(date: String, forward: Boolean) {
    val motion = LocalMotionStyle.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        CalendarGlyph(tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(textSized(CardGlyph)))

        AnimatedContent(
            targetState = date,
            transitionSpec = {
                val direction = if (forward) 1 else -1
                (slideInHorizontally(motion.settle()) { it / 4 * direction } + fadeIn()) togetherWith
                    (slideOutHorizontally(motion.settle()) { -it / 4 * direction } + fadeOut()) using
                    SizeTransform(clip = false)
            },
            label = "readoutDate",
        ) { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The run of days the selected day belongs to, by a flame. */
@Composable
private fun StreakChip(days: Int, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Space.md, vertical = Space.xs),
    ) {
        FlameGlyph(tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(textSized(CardGlyph)))

        Text(
            text = streakLine(days),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}

/**
 * How the day stands against the period's average day, or null on a day with
 * nothing read - a rest rather than a shortfall - or one still to come.
 */
@Composable
private fun averageLine(day: DayReading, averageMs: Long): String? {
    if (day.isFuture || day.durationMs <= 0 || averageMs <= 0) return null

    val difference = day.durationMs - averageMs

    return when {
        difference.absoluteValue < AboutAverageMs -> stringResource(Res.string.stats_about_average)
        difference > 0 -> stringResource(Res.string.stats_more_than_average, readingTime(difference))
        else -> stringResource(Res.string.stats_less_than_average, readingTime(-difference))
    }
}

/**
 * Text whose changed characters roll into place, like an odometer: up for a day
 * later than the one before, down for an earlier one.
 *
 * Keyed from the end, so "1h 24m" becoming "1h 05m" rolls only the minutes, and
 * "45m" becoming "1h 05m" keeps the unit still while the figures change.
 */
@Composable
internal fun RollingText(
    text: String,
    forward: Boolean,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val motion = LocalMotionStyle.current

    if (!motion.flourishes) {
        Text(text = text, style = style, color = color, maxLines = 1, modifier = modifier)
        return
    }

    Row(modifier = modifier) {
        text.forEachIndexed { index, char ->
            key(text.length - index) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        val direction = if (forward) 1 else -1
                        (slideInVertically(motion.settle()) { it * direction } + fadeIn()) togetherWith
                            (slideOutVertically(motion.settle()) { -it * direction } + fadeOut()) using
                            SizeTransform(clip = true)
                    },
                    label = "rollingChar",
                ) { shown ->
                    Text(text = shown.toString(), style = style, color = color, maxLines = 1)
                }
            }
        }
    }
}

/** Whether [date] is later than, or the same as, the one shown before it. */
@Composable
private fun rememberForward(date: LocalDate): Boolean {
    // Plain rather than state: it is only ever read while composing the next day.
    val last = remember { LastDate(date) }
    val forward = remember(date) { date >= last.date }

    SideEffect { last.date = date }

    return forward
}

private class LastDate(var date: LocalDate)
