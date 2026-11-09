package io.lukia.features.stats.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.lukia.core.domain.runCatchingApp
import io.lukia.core.domain.toAppError
import io.lukia.core.presentation.UiState
import io.lukia.features.stats.application.GetReadingStatsUseCase
import io.lukia.features.stats.domain.PeriodKind
import io.lukia.features.stats.domain.ReadingSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    private val _selection = MutableStateFlow(PeriodSelection())

    /**
     * Which week, month or year the reading time card shows.
     *
     * Held here rather than in the screen, so it survives a trip into a book and
     * back - this ViewModel outlives one - without being stored anywhere.
     */
    val selection: StateFlow<PeriodSelection> = _selection.asStateFlow()

    /** Shows the current period of [kind]. */
    fun selectPeriod(kind: PeriodKind) {
        _selection.value = PeriodSelection(kind = kind)
    }

    /** Steps [by] periods later, or earlier when negative, never past the current one. */
    fun stepPeriod(by: Int) {
        _selection.update { it.copy(offset = (it.offset + by).coerceAtMost(0)) }
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

/** A week, month or year, [offset] periods back from the current one. */
data class PeriodSelection(
    val kind: PeriodKind = PeriodKind.Week,
    val offset: Int = 0,
)
