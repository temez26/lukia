package io.lukia.features.reader.infrastructure

import io.lukia.core.domain.FakeSettingsStore
import io.lukia.features.reader.domain.FigureSize
import io.lukia.features.reader.domain.LineSpacing
import io.lukia.features.reader.domain.PdfLayout
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.domain.ReaderTheme
import io.lukia.features.reader.domain.ReaderTypeface
import io.lukia.features.reader.domain.ReadingWidth
import io.lukia.features.reader.domain.TextAlignment
import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderSettingsRepositoryTest {

    @Test
    fun aFirstRunGetsTheDefaults() {
        val repository = ReaderSettingsRepository(FakeSettingsStore())

        assertEquals(ReaderSettings(), repository.settings.value)
    }

    @Test
    fun aChangeIsVisibleImmediately() {
        val repository = ReaderSettingsRepository(FakeSettingsStore())

        repository.update(ReaderSettings(theme = ReaderTheme.Sepia))

        assertEquals(ReaderTheme.Sepia, repository.settings.value.theme)
    }

    /** The point of the whole thing: settings outlive the session. */
    @Test
    fun settingsSurviveBeingReopened() {
        val store = FakeSettingsStore()
        val settings = ReaderSettings(
            theme = ReaderTheme.Dark,
            textScale = 1.4f,
            lineSpacing = LineSpacing.Relaxed,
            pageScale = ReadingWidth.Narrow.fraction,
            typeface = ReaderTypeface.Serif,
        )

        ReaderSettingsRepository(store).update(settings)

        assertEquals(settings, ReaderSettingsRepository(store).settings.value)
    }

    @Test
    fun everySettingIsStoredSeparately() {
        val store = FakeSettingsStore()

        ReaderSettingsRepository(store).update(
            ReaderSettings(theme = ReaderTheme.Sepia, pageScale = ReadingWidth.Medium.fraction),
        )

        assertEquals(ReaderTheme.Sepia.name, store.values["reader.theme"])
        assertEquals(ReadingWidth.Medium.fraction.toString(), store.values["reader.pageScale"])
    }

    /**
     * A value written by an older build, or edited by hand, must cost only the
     * setting it belongs to - not everything else the reader remembered.
     */
    @Test
    fun anUnreadableValueFallsBackWithoutLosingTheRest() {
        val store = FakeSettingsStore(
            mapOf(
                "reader.theme" to "Neon",
                "reader.readingWidth" to ReadingWidth.Narrow.name,
            ),
        )

        val settings = ReaderSettingsRepository(store).settings.value

        assertEquals(ReaderSettings().theme, settings.theme)
        assertEquals(ReadingWidth.Narrow, settings.matchingWidth)
    }

    @Test
    fun aTextSizeThatIsNotANumberFallsBackToTheDefault() {
        val store = FakeSettingsStore(mapOf("reader.textScale" to "huge"))

        assertEquals(
            ReaderSettings().textScale,
            ReaderSettingsRepository(store).settings.value.textScale,
        )
    }

    /** A stored size outside the range still has to come back inside it. */
    @Test
    fun aStoredTextSizeIsClampedOnTheWayBackIn() {
        val store = FakeSettingsStore(mapOf("reader.textScale" to "12.0"))

        assertEquals(
            ReaderSettings.MAX_TEXT_SCALE,
            ReaderSettingsRepository(store).settings.value.textScale,
        )
    }

    /**
     * The panel hands back a whole settings value on every interaction, so
     * re-selecting what is already chosen must not write to storage.
     */
    @Test
    fun settingTheSameValueDoesNotWrite() {
        val store = FakeSettingsStore()
        val repository = ReaderSettingsRepository(store)

        repository.update(ReaderSettings(theme = ReaderTheme.Dark))
        val writesAfterFirstChange = store.writeCount
        repository.update(ReaderSettings(theme = ReaderTheme.Dark))

        assertEquals(writesAfterFirstChange, store.writeCount)
    }

    @Test
    fun theChosenImageSizeIsReadBack() {
        val store = FakeSettingsStore()

        ReaderSettingsRepository(store).update(ReaderSettings(figureSize = FigureSize.Fill))

        assertEquals(FigureSize.Fill, ReaderSettingsRepository(store).settings.value.figureSize)
    }

    /** One key that cannot be read back must not reset the rest of the reader. */
    @Test
    fun anImageSizeThatCannotBeReadBackFallsBackToItsDefault() {
        val store = FakeSettingsStore()
        ReaderSettingsRepository(store).update(ReaderSettings(typeface = ReaderTypeface.Serif))
        store.putString("reader.figureSize", "Enormous")

        val settings = ReaderSettingsRepository(store).settings.value

        assertEquals(FigureSize.Normal, settings.figureSize)
        assertEquals(ReaderTypeface.Serif, settings.typeface)
    }

    @Test
    fun theChosenAlignmentIsReadBack() {
        val store = FakeSettingsStore()

        ReaderSettingsRepository(store).update(
            ReaderSettings(alignment = TextAlignment.Justified),
        )

        assertEquals(
            TextAlignment.Justified,
            ReaderSettingsRepository(store).settings.value.alignment,
        )
    }

    /** A ragged right edge is easier to read on a narrow column, so it leads. */
    @Test
    fun aBookOpensRagged() {
        assertEquals(
            TextAlignment.Ragged,
            ReaderSettingsRepository(FakeSettingsStore()).settings.value.alignment,
        )
    }

    @Test
    fun whatNewPdfsOpenAsIsReadBack() {
        val store = FakeSettingsStore()

        ReaderSettingsRepository(store).update(
            ReaderSettings(defaultPdfLayout = PdfLayout.Text),
        )

        assertEquals(
            PdfLayout.Text,
            ReaderSettingsRepository(store).settings.value.defaultPdfLayout,
        )
    }

    /** A PDF is what its maker made until somebody asks for otherwise. */
    @Test
    fun aPdfOpensAsItsOwnPages() {
        assertEquals(
            PdfLayout.Original,
            ReaderSettingsRepository(FakeSettingsStore()).settings.value.defaultPdfLayout,
        )
    }
}
