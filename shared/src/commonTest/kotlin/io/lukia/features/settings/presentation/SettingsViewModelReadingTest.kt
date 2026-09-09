package io.lukia.features.settings.presentation

import io.lukia.core.domain.reading.PdfLayout
import io.lukia.core.domain.storage.FakeSecretStore
import io.lukia.core.domain.storage.FakeSettingsStore
import io.lukia.core.domain.storage.SettingsStore
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.domain.ReaderTheme
import io.lukia.features.reader.infrastructure.ReaderSettingsRepository
import io.lukia.features.settings.infrastructure.AppSettingsRepository
import io.lukia.features.translate.application.TranslatePassageUseCase
import io.lukia.features.translate.domain.provider.FakeTranslator
import io.lukia.features.translate.domain.provider.ModelCatalogue
import io.lukia.features.translate.domain.provider.ModelWarmUp
import io.lukia.features.translate.infrastructure.TranslationSettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Reading group's half of the seam between what somebody chooses in Settings
 * and what a book is then set in.
 *
 * These settings were once reachable only from the reader's own panel, beside an
 * open book. The second way in is worth testing: the reader is proved to obey
 * whatever settings it is handed and the store to remember what it is given, and
 * neither says whether this screen puts one into the other.
 */
class SettingsViewModelReadingTest {

    @Test
    fun howABookIsSetGoesWhereTheReaderWillReadIt() {
        val store = FakeSettingsStore()
        val reader = ReaderSettingsRepository(store)

        viewModel(store, reader).setReaderSettings(ReaderSettings(theme = ReaderTheme.Sepia))

        assertEquals(ReaderTheme.Sepia, reader.settings.value.theme)
    }

    /** And is still there the next time the app opens, not only this run of it. */
    @Test
    fun whatNewPdfsOpenAsSurvivesTheAppClosing() {
        val store = FakeSettingsStore()

        viewModel(store, ReaderSettingsRepository(store))
            .setReaderSettings(ReaderSettings(defaultPdfLayout = PdfLayout.Text))

        assertEquals(
            PdfLayout.Text,
            ReaderSettingsRepository(store).settings.value.defaultPdfLayout,
        )
    }

    /**
     * The screen edits one value carried whole, so a change to one setting must
     * not quietly put the rest back to their defaults.
     */
    @Test
    fun changingOneSettingLeavesTheOthersAlone() {
        val store = FakeSettingsStore()
        val reader = ReaderSettingsRepository(store)
        val settings = viewModel(store, reader)

        settings.setReaderSettings(reader.settings.value.copy(theme = ReaderTheme.Dark))
        settings.setReaderSettings(
            reader.settings.value.copy(defaultPdfLayout = PdfLayout.Text),
        )

        assertEquals(ReaderTheme.Dark, reader.settings.value.theme)
        assertEquals(PdfLayout.Text, reader.settings.value.defaultPdfLayout)
    }

    private fun viewModel(
        store: SettingsStore,
        reader: ReaderSettingsRepository,
    ) = SettingsViewModel(
        repository = AppSettingsRepository(store),
        reader = reader,
        translation = TranslationSettingsRepository(store, FakeSecretStore()),
        translatePassage = TranslatePassageUseCase(FakeTranslator()),
        catalogue = ModelCatalogue { emptyList() },
        warmUp = ModelWarmUp { },
    )
}
