package io.lukia.features.reader.infrastructure

import io.lukia.core.domain.SettingsStore
import io.lukia.core.infrastructure.readEnum
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
    private val store: SettingsStore,
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
            pageScale = readPageScale(defaults),
            typeface = store.readEnum(KEY_TYPEFACE, ReaderTypeface.entries, defaults.typeface),
        )
    }

    /**
     * Page scale became a continuous value when pinching arrived. A reader who
     * had already chosen one of the old named widths keeps it: the preset it
     * names is read once and carried across to the scale it stood for.
     */
    private fun readPageScale(defaults: ReaderSettings): Float {
        store.getString(KEY_PAGE_SCALE)?.toFloatOrNull()?.let {
            return defaults.withPageScale(it).pageScale
        }

        val storedPreset = store.getString(KEY_READING_WIDTH)
        val preset = ReadingWidth.entries.firstOrNull { it.name == storedPreset }

        return preset?.let { defaults.withPageScale(it.fraction).pageScale } ?: defaults.pageScale
    }

    private fun save(settings: ReaderSettings) {
        store.putString(KEY_THEME, settings.theme.name)
        store.putString(KEY_TEXT_SCALE, settings.textScale.toString())
        store.putString(KEY_LINE_SPACING, settings.lineSpacing.name)
        store.putString(KEY_PAGE_SCALE, settings.pageScale.toString())
        store.putString(KEY_TYPEFACE, settings.typeface.name)
    }

    private companion object {
        const val KEY_THEME = "reader.theme"
        const val KEY_TEXT_SCALE = "reader.textScale"
        const val KEY_LINE_SPACING = "reader.lineSpacing"
        const val KEY_PAGE_SCALE = "reader.pageScale"

        /** Superseded by [KEY_PAGE_SCALE]; still read so a saved choice survives. */
        const val KEY_READING_WIDTH = "reader.readingWidth"
        const val KEY_TYPEFACE = "reader.typeface"
    }
}
