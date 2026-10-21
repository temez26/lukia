package io.lukia.features.stats.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.stats.domain.DayReading
import io.lukia.features.stats.domain.ReadingHabits
import io.lukia.features.stats.presentation.WeekGlanceViewModel
import io.lukia.features.stats.presentation.dayLabel
import io.lukia.features.stats.presentation.readingTime
import io.lukia.resources.Res
import io.lukia.resources.stats_day_spoken
import io.lukia.resources.stats_open
import io.lukia.resources.stats_this_week
import io.lukia.shared.presentation.components.controls.pressable
import io.lukia.shared.presentation.components.icons.DisclosureChevron
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val BarsHeight: Dp = 80.dp
private val BarGap: Dp = 10.dp
private val BarShape = RoundedCornerShape(6.dp)

/** The stub drawn for a day with no reading, as a share of the bars' height, so the week keeps its shape. */
private const val EmptyBar = 0.06f

/**
 * The least a full bar stands for. Scaled to the week's longest day alone, forty
 * seconds of reading drew as tall a bar as four hours would, and a week barely
 * begun looked like a busy one.
 */
private const val FullBarMs = 30 * 60_000L

/**
 * How far the days' names follow the system font size. Further, and seven of
 * them no longer fit under seven bars: "Wed" was cut to "We", which is no day's
 * name. The time over the bars, which is what the card says, grows in full.
 */
private const val DayNameMaxScale = 1.2f

/** Days already read, a step back from today's bar in the accent. */
private const val PastDayAlpha = 0.5f

private val ChevronSize: Dp = 20.dp

/** The app's disclosure mark points down; this turns it to point the way the glance goes. */
private const val PointForward = -90f

/**
 * The week so far, as a glance on Home that opens the reading statistics.
 *
 * Reads the sittings itself, on each return to Home, which may follow one.
 * Nothing is drawn until they have been read, and nothing if the read fails:
 * the statistics screen it would open is where errors are shown.
 *
 * No surface of its own: Home sets it on a card with its own actions under it.
 */
@Composable
fun WeekGlance(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WeekGlanceViewModel = koinViewModel(),
) {
    val habits by viewModel.habits.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    habits?.let { WeekGlanceContent(habits = it, onClick = onClick, modifier = modifier) }
}

/**
 * This week's reading time, with a bar for each of its days over the day's
 * name - today's in the accent - and the way on to the statistics in its
 * corner. Read out as one line: the week and its time.
 */
@Composable
internal fun WeekGlanceContent(
    habits: ReadingHabits,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(Res.string.stats_this_week)
    val time = readingTime(habits.weekMs)
    val spoken = stringResource(Res.string.stats_day_spoken, title, time)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .pressable(
                interactionSource = remember { MutableInteractionSource() },
                shape = RectangleShape,
                onClickLabel = stringResource(Res.string.stats_open),
                onClick = onClick,
            )
            .padding(Space.lg)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )

            DisclosureChevron(
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(ChevronSize)
                    .graphicsLayer { rotationZ = PointForward },
            )
        }

        Text(
            text = time,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        WeekBars(days = habits.days, modifier = Modifier.padding(top = Space.md))
    }
}

/** Each day's reading against the week's longest, as bars rising from one line over their day's name. */
@Composable
private fun WeekBars(days: List<DayReading>, modifier: Modifier = Modifier) {
    val longest = maxOf(days.maxOfOrNull { it.durationMs } ?: 0L, FullBarMs)

    Column(modifier = modifier.fillMaxWidth()) {
        DayRow(modifier = Modifier.height(BarsHeight), verticalAlignment = Alignment.Bottom) {
            days.forEach { day ->
                val share = (day.durationMs.toFloat() / longest).coerceAtLeast(EmptyBar)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(share)
                        .background(barColor(day), BarShape),
                )
            }
        }

        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(DayNameMaxScale)),
        ) {
            DayRow(modifier = Modifier.padding(top = Space.xs)) {
                days.forEach { day ->
                    Text(
                        text = dayLabel(day.date),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.run { if (day.isToday) primary else onSurfaceVariant },
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** The bars and the names under them, spaced the same so each name sits under its bar. */
@Composable
private fun DayRow(
    modifier: Modifier = Modifier,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(BarGap),
        verticalAlignment = verticalAlignment,
        modifier = modifier.fillMaxWidth(),
        content = content,
    )
}

@Composable
private fun barColor(day: DayReading): Color = when {
    !day.hasReading -> MaterialTheme.colorScheme.outlineVariant
    day.isToday -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = PastDayAlpha)
}
