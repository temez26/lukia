package io.lukia.features.settings.presentation

import androidx.lifecycle.ViewModel
import io.lukia.features.settings.domain.AppSettings
import io.lukia.features.settings.domain.AppTheme
import io.lukia.features.settings.infrastructure.AppSettingsRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * The settings screen's half of the app's settings.
 *
 * There is no [io.lukia.core.presentation.UiState] here and no loading: the store
 * behind this is synchronous by design, so these values are already known by the
 * time the screen is composed. A settings screen that opened on a spinner would
 * be slower than the thing it configures.
 */
class SettingsViewModel(
    private val repository: AppSettingsRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings

    fun setTheme(theme: AppTheme) {
        repository.update(repository.settings.value.copy(theme = theme))
    }
}
