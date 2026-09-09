package io.lukia.features.settings.infrastructure

import io.lukia.core.domain.AppAccent
import io.lukia.core.domain.AppMotion
import io.lukia.core.domain.AppPalette
import io.lukia.core.domain.AppTheme
import io.lukia.core.domain.storage.SettingsStore
import io.lukia.core.infrastructure.PersistedSettings
import io.lukia.core.infrastructure.readEnum
import io.lukia.features.settings.domain.AppSettings
import kotlinx.coroutines.flow.StateFlow

/**
 * The app's own settings, live and persisted.
 *
 * Held as one [StateFlow] and read at the root of the app, so changing the theme
 * repaints every screen rather than only the one that changed it, and is already
 * in place the next time the app opens, before anything is drawn. Shared, hence
 * a `single` in the dependency graph.
 */
class AppSettingsRepository(
    private val store: SettingsStore,
) {

    private val persisted = PersistedSettings(load(), ::save)
    val settings: StateFlow<AppSettings> = persisted.state

    fun update(settings: AppSettings) = persisted.set(settings)

    private fun load(): AppSettings {
        val defaults = AppSettings()

        return AppSettings(
            theme = store.readEnum(KEY_THEME, AppTheme.entries, defaults.theme),
            palette = store.readEnum(KEY_PALETTE, AppPalette.entries, defaults.palette),
            accent = store.readEnum(KEY_ACCENT, AppAccent.entries, defaults.accent),
            motion = store.readEnum(KEY_MOTION, AppMotion.entries, defaults.motion),
            diagnostics = store.getString(KEY_DIAGNOSTICS)?.toBooleanStrictOrNull()
                ?: defaults.diagnostics,
            developerMode = store.getString(KEY_DEVELOPER)?.toBooleanStrictOrNull()
                ?: defaults.developerMode,
        )
    }

    private fun save(settings: AppSettings) {
        store.putString(KEY_THEME, settings.theme.name)
        store.putString(KEY_PALETTE, settings.palette.name)
        store.putString(KEY_ACCENT, settings.accent.name)
        store.putString(KEY_MOTION, settings.motion.name)
        store.putString(KEY_DIAGNOSTICS, settings.diagnostics.toString())
        store.putString(KEY_DEVELOPER, settings.developerMode.toString())
    }

    private companion object {
        const val KEY_THEME = "app.theme"
        const val KEY_PALETTE = "app.palette"
        const val KEY_ACCENT = "app.accent"
        const val KEY_MOTION = "app.motion"
        const val KEY_DIAGNOSTICS = "app.diagnostics"
        const val KEY_DEVELOPER = "app.developerMode"
    }
}
