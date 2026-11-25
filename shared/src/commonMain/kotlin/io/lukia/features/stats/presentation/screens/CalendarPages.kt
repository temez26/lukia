package io.lukia.features.stats.presentation.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import io.lukia.features.stats.domain.PeriodKind
import io.lukia.features.stats.domain.ReadingHistory
import io.lukia.features.stats.domain.ReadingPeriod
import io.lukia.features.stats.domain.defaultDay
import io.lukia.features.stats.domain.periodReading
import io.lukia.features.stats.presentation.CalendarSelection
import io.lukia.features.stats.presentation.components.MonthCalendar
import io.lukia.features.stats.presentation.components.YearMonths
import io.lukia.features.stats.presentation.components.miniMonthOrigin
import io.lukia.features.stats.presentation.components.settle
import io.lukia.shared.presentation.theme.LocalMotionStyle
import io.lukia.shared.presentation.theme.exit
import io.lukia.shared.presentation.theme.standard
import kotlinx.coroutines.flow.drop
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

/** How far into its mini month a zoomed month starts growing from, as a share of its size. */
private const val ZoomFrom = 0.3f

/**
 * The calendar at either level: every month there is reading since, one page
 * each, or every year, one page each - swiped through, or paged with ‹ ›.
 *
 * Zooming grows the month out of its block in the year and shrinks it back, so
 * the reader keeps their place in the year rather than being sent elsewhere and
 * left to find the way back.
 */
@Composable
internal fun CalendarPages(
    history: ReadingHistory,
    selection: CalendarSelection,
    selected: LocalDate,
    columns: Int,
    actions: StatsActions,
    modifier: Modifier = Modifier,
) {
    val motion = LocalMotionStyle.current
    val month = selection.shownMonth(history)
    val origin = miniMonthOrigin(month.start.month, columns)

    AnimatedContent(
        targetState = selection.zoomed,
        transitionSpec = {
            val grow = if (motion.flourishes) {
                scaleIn(motion.settle(), initialScale = ZoomFrom, transformOrigin = origin)
            } else {
                EnterTransition.None
            }
            val shrink = if (motion.flourishes) {
                scaleOut(motion.settle(), targetScale = ZoomFrom, transformOrigin = origin)
            } else {
                ExitTransition.None
            }

            if (targetState) {
                (grow + fadeIn(motion.standard())) togetherWith fadeOut(motion.exit())
            } else {
                fadeIn(motion.standard()) togetherWith (shrink + fadeOut(motion.exit()))
            } using SizeTransform(clip = false) { _, _ -> motion.settle() }
        },
        modifier = modifier.fillMaxWidth(),
        label = "calendarZoom",
    ) { zoomed ->
        if (zoomed) {
            MonthPages(history, month, selected, actions)
        } else {
            YearPages(history, month, selection, columns, actions)
        }
    }
}

@Composable
private fun MonthPages(
    history: ReadingHistory,
    month: ReadingPeriod,
    selected: LocalDate,
    actions: StatsActions,
) {
    val months = remember(history) { history.months }

    Pages(
        count = months.size,
        index = months.indexOf(month),
        onSettle = { actions.onShowMonth(months[it].start) },
    ) { page ->
        val shown = remember(history, page) { history.periodReading(months[page]) }

        MonthCalendar(
            month = shown,
            // A neighbouring month, half swiped in, shows the day it would
            // open on rather than no selection at all.
            selected = if (page == months.indexOf(month)) selected else history.defaultDay(months[page]),
            onSelect = actions.onSelectDay,
        )
    }
}

@Composable
private fun YearPages(
    history: ReadingHistory,
    month: ReadingPeriod,
    selection: CalendarSelection,
    columns: Int,
    actions: StatsActions,
) {
    val years = remember(history) { history.years }
    val reachable = remember(history) { history.months.first().start..history.today }
    val year = month.start.year

    Pages(
        count = years.size,
        index = years.indexOf(year),
        onSettle = { page -> actions.onShowMonth(LocalDate(years[page], month.start.month, 1)) },
    ) { page ->
        val period = ReadingPeriod.containing(PeriodKind.Year, LocalDate(years[page], Month.JANUARY, 1))
        val shown = remember(history, page) { history.periodReading(period) }

        YearMonths(
            year = shown,
            selected = selection.selectedDay(history, period),
            columns = columns,
            reachable = reachable,
            onShowMonth = actions.onZoomInto,
        )
    }
}

/**
 * One page per period, swiped through with the finger and moved by [index]
 * changing - which is what ‹ › do - with [onSettle] told where a swipe stopped.
 *
 * As tall as the page shown: a month of four weeks and one of six are not the
 * same height, and the card follows with a spring rather than a jump.
 */
@Composable
private fun Pages(
    count: Int,
    index: Int,
    onSettle: (Int) -> Unit,
    page: @Composable (Int) -> Unit,
) {
    val motion = LocalMotionStyle.current
    val state = rememberPagerState(initialPage = index) { count }
    val shown by rememberUpdatedState(index)
    val settle by rememberUpdatedState(onSettle)

    LaunchedEffect(index) {
        if (state.currentPage != index) {
            if (motion.flourishes) state.animateScrollToPage(index) else state.scrollToPage(index)
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { state.settledPage }
            .drop(1)
            .collect { settled -> if (settled != shown) settle(settled) }
    }

    HorizontalPager(
        state = state,
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(motion.settle()),
    ) { index ->
        page(index)
    }
}
