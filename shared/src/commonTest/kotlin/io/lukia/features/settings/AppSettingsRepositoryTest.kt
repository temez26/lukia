package io.lukia.features.settings

import io.lukia.core.domain.FakeSettingsStore
import io.lukia.features.settings.domain.AppSettings
import io.lukia.features.settings.domain.AppTheme
import io.lukia.features.settings.infrastructure.AppSettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals

class AppSettingsRepositoryTest {

    @Test
    fun defaultsToFollowingTheSystem() {
        val repository = AppSettingsRepository(FakeSettingsStore())

        assertEquals(AppTheme.System, repository.settings.value.theme)
    }

    @Test
    fun readsAStoredTheme() {
        val store = FakeSettingsStore(mapOf("app.theme" to "Dark"))

        assertEquals(AppTheme.Dark, AppSettingsRepository(store).settings.value.theme)
    }

    @Test
    fun updatingPublishesAndPersists() {
        val store = FakeSettingsStore()
        val repository = AppSettingsRepository(store)

        repository.update(AppSettings(theme = AppTheme.Light))

        assertEquals(AppTheme.Light, repository.settings.value.theme)
        assertEquals("Light", store.values["app.theme"])
    }

    /** Nothing changed is nothing to write - a settings screen re-emits freely. */
    @Test
    fun updatingWithTheSameValueWritesNothing() {
        val store = FakeSettingsStore()
        val repository = AppSettingsRepository(store)

        repository.update(AppSettings(theme = AppTheme.Dark))
        val afterFirst = store.writeCount

        repository.update(AppSettings(theme = AppTheme.Dark))

        assertEquals(afterFirst, store.writeCount)
    }

    /**
     * A value written by an older build, or edited by hand into nonsense, falls
     * back to the default rather than throwing on the way to the first frame.
     */
    @Test
    fun anUnreadableStoredThemeFallsBackToTheDefault() {
        val store = FakeSettingsStore(mapOf("app.theme" to "Solarized"))

        assertEquals(AppTheme.System, AppSettingsRepository(store).settings.value.theme)
    }
}
