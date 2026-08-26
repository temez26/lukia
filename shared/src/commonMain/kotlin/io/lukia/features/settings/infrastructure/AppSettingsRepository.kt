package io.lukia.features.settings.infrastructure

import io.lukia.core.domain.AppAccent
import io.lukia.core.domain.AppMotion
import io.lukia.core.domain.AppPalette
import io.lukia.core.domain.AppTheme
import io.lukia.core.domain.SettingsStore
import io.lukia.core.infrastructure.readEnum
import io.lukia.features.settings.domain.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The app's own settings, live and persisted.
 *
 * Held as one [StateFlow] and read at the root of the app, so changing the theme
 * repaints every screen at once rather than only the one that changed it - and it
 * is already in place the next time the app opens, before anything is drawn.
 *
 * A single instance is shared by the app, which is why it is a `single` in the
 * dependency graph.
 */
class AppSettingsRepository(
    private val store: SettingsStore,
) {

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(settings: AppSettings) {
        if (_settings.value == settings) return

        _settings.value = settings
        save(settings)
    }

    private fun load(): AppSettings {
        val defaults = AppSettings()

        return AppSettings(
            theme = store.readEnum(KEY_THEME, AppTheme.entries, defaults.theme),
            palette = store.readEnum(KEY_PALETTE, AppPalette.entries, defaults.palette),
            accent = store.readEnum(KEY_ACCENT, AppAccent.entries, defaults.accent),
            motion = store.readEnum(KEY_MOTION, AppMotion.entries, defaults.motion),
        )
    }

    private fun save(settings: AppSettings) {
        store.putString(KEY_THEME, settings.theme.name)
        store.putString(KEY_PALETTE, settings.palette.name)
        store.putString(KEY_ACCENT, settings.accent.name)
        store.putString(KEY_MOTION, settings.motion.name)
    }

    private companion object {
        const val KEY_THEME = "app.theme"
        const val KEY_PALETTE = "app.palette"
        const val KEY_ACCENT = "app.accent"
        const val KEY_MOTION = "app.motion"
    }
}
