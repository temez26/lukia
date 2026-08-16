package io.lukia.di

import io.lukia.core.domain.DocumentWorkspace
import io.lukia.core.domain.SettingsStore
import io.lukia.features.reader.application.OpenDocumentUseCase
import io.lukia.features.reader.infrastructure.ReaderSettingsRepository
import io.lukia.features.library.application.RemoveDocumentUseCase
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/**
 * A missing or miswired dependency is otherwise only found by opening the screen
 * that needs it, on the platform that provides it. This resolves the graph the
 * desktop build actually runs with, so a registration that was forgotten fails
 * here instead.
 */
class AppGraphTest {

    private val koin = koinApplication {
        modules(commonModule, platformModule)
    }.koin

    @Test
    fun theDesktopPlatformProvidesItsHalfOfTheGraph() {
        assertNotNull(koin.get<SettingsStore>())
        assertNotNull(koin.get<DocumentWorkspace>())
    }

    @Test
    fun theReaderResolves() {
        assertNotNull(koin.get<OpenDocumentUseCase>())
        assertNotNull(koin.get<ReaderSettingsRepository>())
    }

    /**
     * Reader settings are shared state - a second instance would mean a change
     * made in one place quietly not reaching another.
     */
    @Test
    fun readerSettingsAreOneSharedInstance() {
        assertSame(koin.get<ReaderSettingsRepository>(), koin.get<ReaderSettingsRepository>())
    }

    /** Removing a book has to be able to discard its working copy. */
    @Test
    fun removingADocumentResolvesWithTheWorkspace() {
        assertNotNull(koin.get<RemoveDocumentUseCase>())
    }
}
