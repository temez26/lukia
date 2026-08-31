package io.lukia.di

import app.cash.sqldelight.db.SqlDriver
import io.ktor.client.HttpClient
import io.lukia.core.domain.reading.ReadingProgressRepository
import io.lukia.core.domain.storage.DocumentWorkspace
import io.lukia.core.domain.storage.SecretStore
import io.lukia.core.domain.storage.SettingsStore
import io.lukia.core.infrastructure.lukiaSqlDriver
import io.lukia.features.library.application.RemoveDocumentUseCase
import io.lukia.features.reader.application.OpenDocumentUseCase
import io.lukia.features.reader.infrastructure.ReaderSettingsRepository
import io.lukia.features.settings.infrastructure.AppSettingsRepository
import io.lukia.features.translate.application.TranslatePassageUseCase
import io.lukia.features.translate.domain.TranslationCache
import io.lukia.features.translate.domain.Translator
import io.lukia.features.translate.domain.provider.ModelCatalogue
import io.lukia.features.translate.infrastructure.TranslationSettingsRepository
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.io.File
import kotlin.test.AfterTest
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

    /**
     * The desktop build's own driver, pointed somewhere temporary. Resolving
     * the graph opens the database, and a test has no business opening - or
     * creating - the one the installed app reads.
     */
    private val databaseFile = File(
        System.getProperty("java.io.tmpdir"),
        "lukia-graph-test-${kotlin.random.Random.nextInt(until = Int.MAX_VALUE)}.db",
    )

    private val koin = koinApplication {
        modules(
            commonModule,
            platformModule,
            module { single<SqlDriver> { lukiaSqlDriver(databaseFile) } },
        )
    }.koin

    @AfterTest
    fun tearDown() {
        koin.get<SqlDriver>().close()
        databaseFile.delete()
    }

    /**
     * Everything that has to be stored between runs hangs off one database, and
     * a missing driver would only show up as an empty library on launch.
     */
    @Test
    fun whatIsStoredBetweenRunsResolves() {
        assertNotNull(koin.get<SqlDriver>())
        assertNotNull(koin.get<ReadingProgressRepository>())
    }

    @Test
    fun theDesktopPlatformProvidesItsHalfOfTheGraph() {
        assertNotNull(koin.get<SettingsStore>())
        assertNotNull(koin.get<DocumentWorkspace>())
    }

    /**
     * A secret is stored differently from a preference on every platform, so a
     * missing registration here would mean the reader's key silently going
     * nowhere - or, worse, into the settings file.
     */
    @Test
    fun somewhereToKeepASecretResolves() {
        assertNotNull(koin.get<SecretStore>())
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

    /**
     * The app's theme is resolved at the root of the composition, before any
     * screen exists - so a missing registration here would not fail on some
     * screen nobody opened, it would fail on launch.
     */
    @Test
    fun appSettingsResolve() {
        assertNotNull(koin.get<AppSettingsRepository>())
    }

    /**
     * Shared state, for the same reason as the reader's: the settings screen and
     * the root of the app both read this, and two instances would mean changing
     * the theme repainted nothing.
     */
    @Test
    fun appSettingsAreOneSharedInstance() {
        assertSame(koin.get<AppSettingsRepository>(), koin.get<AppSettingsRepository>())
    }

    /**
     * The reader asks for these the first time somebody taps a sentence. A
     * missing registration would otherwise only show up as a book that quietly
     * did nothing when tapped.
     */
    @Test
    fun translatingResolves() {
        assertNotNull(koin.get<Translator>())
        assertNotNull(koin.get<TranslatePassageUseCase>())
        assertNotNull(koin.get<TranslationSettingsRepository>())
        assertNotNull(koin.get<ModelCatalogue>())
    }

    /**
     * One client for the app rather than one per provider: connections are kept
     * and reused between taps, and two would mean neither ever warm.
     */
    @Test
    fun everyProviderSharesOneHttpClient() {
        assertSame(koin.get<HttpClient>(), koin.get<HttpClient>())
    }

    /**
     * Where passages already paid for are kept. A missing registration would not
     * fail loudly - translating would carry on working, and quietly charge for
     * every sentence a second time.
     */
    @Test
    fun theStoreOfAlreadyTranslatedPassagesResolves() {
        assertNotNull(koin.get<TranslationCache>())
    }

    /**
     * One cache for the app, over the one database. A second instance would be a
     * second store nothing else could see into.
     */
    @Test
    fun theTranslationCacheIsOneSharedInstance() {
        assertSame(koin.get<TranslationCache>(), koin.get<TranslationCache>())
    }

    /**
     * Shared state again: the language is chosen on the settings screen and read
     * by the open reader, and two instances would mean choosing one changed
     * nothing about the book in front of you.
     */
    @Test
    fun translationSettingsAreOneSharedInstance() {
        assertSame(
            koin.get<TranslationSettingsRepository>(),
            koin.get<TranslationSettingsRepository>(),
        )
    }
}
