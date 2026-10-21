package io.lukia.features.stats.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.stats.domain.DayReading
import io.lukia.features.stats.domain.ReadingHabits
import io.lukia.features.stats.presentation.WeekGlanceViewModel
import io.lukia.features.stats.presentation.readingTime
import io.lukia.resources.Res
import io.lukia.resources.stats_day_spoken
import io.lukia.resources.stats_open
import io.lukia.resources.stats_this_week
import io.lukia.shared.presentation.components.controls.pressable
import io.lukia.shared.presentation.components.icons.DisclosureChevron
import io.lukia.shared.presentation.components.surface.GroupCard
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** A week's bars, Monday to Sunday, however few of its days have been read. */
private const val DaysInWeek = 7

private val BarWidth: Dp = 6.dp
private val BarGap: Dp = 5.dp
private val BarsHeight: Dp = 36.dp

/** The stub drawn for a day with no reading, so the week keeps its shape. */
private val EmptyBar: Dp = 3.dp

/** Days already read, a step back from today's bar in the accent. */
private const val PastDayAlpha = 0.5f

private val ChevronSize: Dp = 18.dp

/** The app's disclosure mark points down; this turns it to point the way the card goes. */
private const val PointForward = -90f

/**
 * The week so far, as a glance on Home that opens the reading statistics.
 *
 * Reads the sittings itself, on each return to Home, which may follow one.
 * Nothing is drawn until they have been read, and nothing if the read fails:
 * the statistics screen it would open is where errors are shown.
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

    habits?.let { WeekGlanceCard(habits = it, onClick = onClick, modifier = modifier) }
}

/**
 * This week's reading time, with a bar for each of its days - today's in the
 * accent - on a card of its own, as the shelves under it are.
 */
@Composable
internal fun WeekGlanceCard(
    habits: ReadingHabits,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(Res.string.stats_this_week)
    val time = readingTime(habits.weekMs)
    val spoken = stringResource(Res.string.stats_day_spoken, title, time)
    val shape = MaterialTheme.shapes.large

    GroupCard(
        modifier = modifier.pressable(
            interactionSource = remember { MutableInteractionSource() },
            shape = shape,
            onClickLabel = stringResource(Res.string.stats_open),
            onClick = onClick,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(Space.lg)
                .clearAndSetSemantics { contentDescription = spoken },
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = time,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            WeekBars(days = habits.days)

            Spacer(modifier = Modifier.width(Space.md))

            DisclosureChevron(
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(ChevronSize)
                    .graphicsLayer { rotationZ = PointForward },
            )
        }
    }
}

/** Each day's reading against the week's longest, as small bars rising from one line. */
@Composable
private fun WeekBars(days: List<DayReading>) {
    val today = MaterialTheme.colorScheme.primary
    val past = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = PastDayAlpha)
    val empty = MaterialTheme.colorScheme.outlineVariant
    val longest = days.maxOfOrNull { it.durationMs }?.takeIf { it > 0 } ?: 1L

    Canvas(modifier = Modifier.size(width = BarWidth * DaysInWeek + BarGap * (DaysInWeek - 1), height = BarsHeight)) {
        val width = BarWidth.toPx()
        val radius = CornerRadius(width / 2)

        repeat(DaysInWeek) { index ->
            val day = days.getOrNull(index)?.takeIf { it.hasReading }
            val height = day?.let { (size.height * it.durationMs / longest).coerceAtLeast(EmptyBar.toPx()) }
                ?: EmptyBar.toPx()
            val color = when {
                day == null -> empty
                day.isToday -> today
                else -> past
            }

            drawRoundRect(
                color = color,
                topLeft = Offset(index * (width + BarGap.toPx()), size.height - height),
                size = Size(width, height),
                cornerRadius = radius,
            )
        }
    }
}
