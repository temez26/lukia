package io.lukia.features.stats.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.lukia.core.domain.runCatchingApp
import io.lukia.features.stats.application.GetReadingHabitsUseCase
import io.lukia.features.stats.domain.ReadingHabits
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The week so far, for the glance at it on Home.
 *
 * Null until it is read, and left as it was if a read fails: a glance has no
 * room for an error, and the statistics screen it opens has one of its own.
 */
class WeekGlanceViewModel(
    private val getReadingHabits: GetReadingHabitsUseCase,
) : ViewModel() {

    private val _habits = MutableStateFlow<ReadingHabits?>(null)
    val habits: StateFlow<ReadingHabits?> = _habits.asStateFlow()

    /** Reads the sittings again - on each return to Home, which may follow a sitting. */
    fun refresh() {
        viewModelScope.launch {
            runCatchingApp { getReadingHabits() }.onSuccess { _habits.value = it }
        }
    }
}
