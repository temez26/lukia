package io.lukia.features.stats.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.lukia.core.domain.runCatchingApp
import io.lukia.core.domain.toAppError
import io.lukia.core.presentation.UiState
import io.lukia.features.stats.application.GetReadingStatsUseCase
import io.lukia.features.stats.domain.PeriodKind
import io.lukia.features.stats.domain.ReadingHistory
import io.lukia.features.stats.domain.ReadingPeriod
import io.lukia.features.stats.domain.ReadingSummary
import io.lukia.features.stats.domain.defaultDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * What the reading adds up to, and nothing else.
 *
 * One flow, because the screen has one subject. Everything on it is a read of
 * the whole library and the sittings recorded against it, which can be slow and
 * can fail, so it gets the app's usual three states rather than being exposed
 * raw - there is no part of this screen that can be drawn before the count
 * lands.
 */
class StatsViewModel(
    private val getReadingStats: GetReadingStatsUseCase,
) : ViewModel() {

    private val _summary = MutableStateFlow<UiState<ReadingSummary>>(UiState.Loading)
    val summary: StateFlow<UiState<ReadingSummary>> = _summary.asStateFlow()

    /**
     * Reads the library, the positions and the sittings.
     *
     * Driven by the screen entering rather than by this being constructed, and
     * not by both. This ViewModel outlives a trip into a book - the statistics
     * are reached from a control on the shelf and left by going back to it - so
     * the screen has to ask again on every entry anyway, and asking here as well
     * only put a second read of the whole library in flight beside the first, at
     * the moment the screen was still sliding in over the shelf.
     */
    private val _selection = MutableStateFlow(CalendarSelection())

    /**
     * Which month or year the reading time card shows, and which day in it.
     *
     * Held here rather than in the screen, so it survives a trip into a book and
     * back - this ViewModel outlives one - without being stored anywhere.
     */
    val selection: StateFlow<CalendarSelection> = _selection.asStateFlow()

    /** Shows the month starting [month], at whichever level is shown. */
    fun showMonth(month: LocalDate) {
        _selection.update { it.copy(month = month, day = null) }
    }

    fun selectDay(day: LocalDate) {
        _selection.update { it.copy(day = day) }
    }

    /** Opens the month starting [month] out of its year. */
    fun zoomInto(month: LocalDate) {
        _selection.update { CalendarSelection(month = month, zoomed = true) }
    }

    /** Back into the month the year was opened from, its day still selected if it lies there. */
    fun zoomIn() {
        _selection.update { it.copy(zoomed = true) }
    }

    /** Shows the whole year of the month shown, with [day] still selected. */
    fun zoomOut(day: LocalDate) {
        _selection.update { it.copy(day = day, zoomed = false) }
    }

    fun refresh() {
        viewModelScope.launch {
            _summary.value = runCatchingApp { getReadingStats() }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Failure(it.toAppError()) },
            )
        }
    }
}

/**
 * The reading time card's place in the calendar: a month, and whether it is
 * shown on its own or as part of its year.
 */
data class CalendarSelection(
    /** The first of the month shown, or null for the current month. */
    val month: LocalDate? = null,
    /** The day tapped, or null for the period's default - see [defaultDay]. */
    val day: LocalDate? = null,
    val zoomed: Boolean = true,
) {
    /** The month shown, held to the months [history] can reach. */
    fun shownMonth(history: ReadingHistory): ReadingPeriod {
        val months = history.months
        val wanted = month ?: return months.last()

        return months.lastOrNull { it.start <= wanted } ?: months.first()
    }

    /** The month shown, or its year when zoomed out. */
    fun shownPeriod(history: ReadingHistory): ReadingPeriod {
        val month = shownMonth(history)

        return if (zoomed) month else ReadingPeriod.containing(PeriodKind.Year, month.start)
    }

    /** The day shown as selected in [period]: the one tapped, if it lies in it. */
    fun selectedDay(history: ReadingHistory, period: ReadingPeriod): LocalDate =
        day?.takeIf { it in period } ?: history.defaultDay(period)
}
