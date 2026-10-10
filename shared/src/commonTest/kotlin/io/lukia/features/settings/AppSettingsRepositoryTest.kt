package io.lukia.features.settings

import io.lukia.core.domain.AppAccent
import io.lukia.core.domain.AppPalette
import io.lukia.core.domain.AppTheme
import io.lukia.core.domain.storage.FakeSettingsStore
import io.lukia.features.settings.domain.AppSettings
import io.lukia.features.settings.infrastructure.PersistedAppSettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals

class AppSettingsRepositoryTest {

    @Test
    fun defaultsToFollowingTheSystem() {
        val repository = PersistedAppSettingsRepository(FakeSettingsStore())

        assertEquals(AppTheme.System, repository.settings.value.theme)
    }

    @Test
    fun readsAStoredTheme() {
        val store = FakeSettingsStore(mapOf("app.theme" to "Dark"))

        assertEquals(AppTheme.Dark, PersistedAppSettingsRepository(store).settings.value.theme)
    }

    @Test
    fun updatingPublishesAndPersists() {
        val store = FakeSettingsStore()
        val repository = PersistedAppSettingsRepository(store)

        repository.update(AppSettings(theme = AppTheme.Light))

        assertEquals(AppTheme.Light, repository.settings.value.theme)
        assertEquals("Light", store.values["app.theme"])
    }

    /** Nothing changed is nothing to write - a settings screen re-emits freely. */
    @Test
    fun updatingWithTheSameValueWritesNothing() {
        val store = FakeSettingsStore()
        val repository = PersistedAppSettingsRepository(store)

        repository.update(AppSettings(theme = AppTheme.Dark))
        val afterFirst = store.writeCount

        repository.update(AppSettings(theme = AppTheme.Dark))

        assertEquals(afterFirst, store.writeCount)
    }

    /** The app opens in its own colours until somebody says otherwise. */
    @Test
    fun defaultsToTheAppsOwnPaletteAndItsPalettesOwnAccent() {
        val settings = PersistedAppSettingsRepository(FakeSettingsStore()).settings.value

        assertEquals(AppPalette.Crisp, settings.palette)
        assertEquals(AppAccent.Themed, settings.accent)
    }

    @Test
    fun readsAStoredPaletteAndAccent() {
        val store = FakeSettingsStore(
            mapOf("app.palette" to "Slate", "app.accent" to "Rose"),
        )

        val settings = PersistedAppSettingsRepository(store).settings.value

        assertEquals(AppPalette.Slate, settings.palette)
        assertEquals(AppAccent.Rose, settings.accent)
    }

    @Test
    fun aChosenPaletteAndAccentAreStillThereNextTime() {
        val store = FakeSettingsStore()

        PersistedAppSettingsRepository(store).update(
            AppSettings(palette = AppPalette.Paper, accent = AppAccent.Green),
        )

        assertEquals("Paper", store.values["app.palette"])
        assertEquals("Green", store.values["app.accent"])
        // ...and read back by the next run, which is the point of writing them.
        val reopened = PersistedAppSettingsRepository(store).settings.value
        assertEquals(AppPalette.Paper, reopened.palette)
        assertEquals(AppAccent.Green, reopened.accent)
    }

    /**
     * The developer settings are hidden until somebody is talked through finding
     * them, and stay found afterwards - a reader working through a bug report
     * should not have to press the version again after every restart.
     */
    @Test
    fun theDeveloperSettingsAreOffUntilAskedForAndStayOnOnceTheyAre() {
        val store = FakeSettingsStore()

        assertEquals(false, PersistedAppSettingsRepository(store).settings.value.developerMode)

        PersistedAppSettingsRepository(store).update(AppSettings(developerMode = true))

        assertEquals(true, PersistedAppSettingsRepository(store).settings.value.developerMode)
    }

    /**
     * A value written by an older build, or edited by hand into nonsense, falls
     * back to the default rather than throwing on the way to the first frame.
     */
    @Test
    fun anUnreadableStoredThemeFallsBackToTheDefault() {
        val store = FakeSettingsStore(mapOf("app.theme" to "Solarized"))

        assertEquals(AppTheme.System, PersistedAppSettingsRepository(store).settings.value.theme)
    }

    @Test
    fun anUnreadableStoredPaletteOrAccentFallsBackToTheDefault() {
        val store = FakeSettingsStore(
            mapOf("app.palette" to "Kindle", "app.accent" to "Chartreuse"),
        )

        val settings = PersistedAppSettingsRepository(store).settings.value

        assertEquals(AppPalette.Crisp, settings.palette)
        assertEquals(AppAccent.Themed, settings.accent)
    }
}
