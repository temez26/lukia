package io.lukia.features.reader.infrastructure

import io.lukia.core.domain.SettingsStore
import io.lukia.features.reader.domain.LineSpacing
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.domain.ReaderTheme
import io.lukia.features.reader.domain.ReaderTypeface
import io.lukia.features.reader.domain.ReadingWidth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The reader's settings, live and persisted.
 *
 * Held as one [StateFlow] rather than read per screen so a change reaches the
 * open reader immediately and is still there the next time a book is opened.
 * A single instance is shared by the app, which is why it is a `single` in the
 * dependency graph.
 *
 * Each setting is stored under its own key. A value that cannot be read back -
 * written by an older build, or edited by hand into nonsense - falls back to
 * that setting's default rather than discarding the rest, so one bad key cannot
 * reset someone's whole reader.
 */
class ReaderSettingsRepository(
    private val store: SettingsStore
) {

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<ReaderSettings> = _settings.asStateFlow()

    fun update(settings: ReaderSettings) {
        if (_settings.value == settings) return

        _settings.value = settings
        save(settings)
    }

    private fun load(): ReaderSettings {
        val defaults = ReaderSettings()

        return ReaderSettings(
            theme = store.readEnum(KEY_THEME, ReaderTheme.entries, defaults.theme),
            textScale = store.getString(KEY_TEXT_SCALE)?.toFloatOrNull()
                ?.let { defaults.withTextScale(it).textScale }
                ?: defaults.textScale,
            lineSpacing = store.readEnum(KEY_LINE_SPACING, LineSpacing.entries, defaults.lineSpacing),
            readingWidth = store.readEnum(KEY_READING_WIDTH, ReadingWidth.entries, defaults.readingWidth),
            typeface = store.readEnum(KEY_TYPEFACE, ReaderTypeface.entries, defaults.typeface)
        )
    }

    private fun save(settings: ReaderSettings) {
        store.putString(KEY_THEME, settings.theme.name)
        store.putString(KEY_TEXT_SCALE, settings.textScale.toString())
        store.putString(KEY_LINE_SPACING, settings.lineSpacing.name)
        store.putString(KEY_READING_WIDTH, settings.readingWidth.name)
        store.putString(KEY_TYPEFACE, settings.typeface.name)
    }

    /** Stored by name, so the order of an enum's entries is free to change. */
    private fun <T : Enum<T>> SettingsStore.readEnum(
        key: String,
        entries: List<T>,
        default: T
    ): T {
        val stored = getString(key) ?: return default
        return entries.firstOrNull { it.name == stored } ?: default
    }

    private companion object {
        const val KEY_THEME = "reader.theme"
        const val KEY_TEXT_SCALE = "reader.textScale"
        const val KEY_LINE_SPACING = "reader.lineSpacing"
        const val KEY_READING_WIDTH = "reader.readingWidth"
        const val KEY_TYPEFACE = "reader.typeface"
    }
}
