package io.lukia.features.translate.infrastructure

import io.lukia.core.domain.SecretStore
import io.lukia.core.domain.SettingsStore
import io.lukia.core.infrastructure.PersistedSettings
import io.lukia.core.infrastructure.readEnum
import io.lukia.features.translate.domain.BulkTranslation
import io.lukia.features.translate.domain.PassageUnit
import io.lukia.features.translate.domain.TargetLanguage
import io.lukia.features.translate.domain.TranslationGestures
import io.lukia.features.translate.domain.TranslationProvider
import io.lukia.features.translate.domain.TranslationSettings
import io.lukia.features.translate.domain.WordLookup
import kotlinx.coroutines.flow.StateFlow

/**
 * How translating is configured, live and persisted - the same shape as the
 * reader's and the app's own settings, and a single instance for the same
 * reason: a language chosen on the settings screen has to be the language the
 * open book uses on the very next tap.
 *
 * Each provider's key, model and address are stored under keys of their own, so
 * a reader who has set up two of them keeps both. A value that cannot be read
 * back - written by an older build, or edited by hand into nonsense - falls back
 * to that setting's default rather than discarding the rest.
 */
class TranslationSettingsRepository(
    private val store: SettingsStore,
    /**
     * Keys are kept apart from every other setting, in whatever the platform
     * actually offers for a secret. See [SecretStore] for why the two are not
     * the same store wearing different names.
     */
    private val secrets: SecretStore,
) {

    private val persisted = PersistedSettings(load(), ::save)
    val settings: StateFlow<TranslationSettings> = persisted.state

    fun update(settings: TranslationSettings) = persisted.set(settings)

    private fun load(): TranslationSettings {
        val defaults = TranslationSettings()

        return TranslationSettings.stored(
            targetLanguage = store.readEnum(
                KEY_TARGET_LANGUAGE,
                TargetLanguage.entries,
                defaults.targetLanguage,
            ),
            provider = store.readEnum(KEY_PROVIDER, TranslationProvider.entries, defaults.provider),
            models = readPerProvider(KEY_MODEL),
            keys = readKeys(),
            baseUrls = readPerProvider(KEY_BASE_URL),
            timeouts = readTimeouts(),
            wordLookup = store.readEnum(KEY_WORD_LOOKUP, WordLookup.entries, defaults.wordLookup),
            googleKey = secrets.getSecret(KEY_GOOGLE_KEY)?.takeIf { it.isNotBlank() },
            gestures = readGestures(defaults.gestures),
            bulkTranslation = BulkTranslation(
                onScreen = store.readFlag(KEY_TRANSLATE_ON_SCREEN),
                asYouRead = store.readFlag(KEY_TRANSLATE_AS_YOU_READ),
            ),
        )
    }

    private fun save(settings: TranslationSettings) {
        store.putString(KEY_TARGET_LANGUAGE, settings.targetLanguage.name)
        store.putString(KEY_PROVIDER, settings.provider.name)

        writePerProvider(KEY_MODEL, settings.storedModels())
        writeKeys(settings.storedKeys())
        writePerProvider(KEY_BASE_URL, settings.storedBaseUrls())
        writePerProvider(KEY_TIMEOUT, settings.storedTimeouts().mapValues { it.value.toString() })

        store.putString(KEY_WORD_LOOKUP, settings.wordLookup.name)
        secrets.putSecret(KEY_GOOGLE_KEY, settings.storedGoogleKey())
        writeGestures(settings.gestures)

        store.putString(KEY_TRANSLATE_ON_SCREEN, settings.bulkTranslation.onScreen.toString())
        store.putString(KEY_TRANSLATE_AS_YOU_READ, settings.bulkTranslation.asYouRead.toString())
    }

    /**
     * Keys, out of the secret store - and out of the settings file as well, for
     * anyone upgrading from a build that kept them there. A key found in the old
     * place is moved rather than copied, so it does not stay behind in plain
     * text once it is somewhere better.
     */
    private fun readKeys(): Map<TranslationProvider, String> =
        TranslationProvider.entries.mapNotNull { provider ->
            val name = provider.name
            val secret = secrets.getSecret("$KEY_API_KEY.$name")
                ?: store.getString("$KEY_API_KEY.$name")?.takeIf { it.isNotBlank() }?.also {
                    secrets.putSecret("$KEY_API_KEY.$name", it)
                    store.putString("$KEY_API_KEY.$name", "")
                }

            secret?.let { provider to it }
        }.toMap()

    private fun writeKeys(values: Map<TranslationProvider, String>) {
        TranslationProvider.entries.forEach { provider ->
            secrets.putSecret("$KEY_API_KEY.${provider.name}", values[provider].orEmpty())
        }
    }

    /**
     * A wait that is not a number is a value from an older build or a hand-edited
     * file, and is left out rather than argued with - the provider's own default
     * is a better answer than a stored one nobody can read.
     */
    private fun readTimeouts(): Map<TranslationProvider, Int> =
        readPerProvider(KEY_TIMEOUT).mapNotNull { (provider, stored) ->
            stored.toIntOrNull()?.let { provider to it }
        }.toMap()

    /**
     * What a tap and a long press do. A gesture is stored as the unit it asks
     * about, and turned off as an empty value - which reads back as null rather
     * than as a unit nobody chose, and leaves an older build's settings file
     * meaning what it meant.
     */
    private fun readGestures(defaults: TranslationGestures) = TranslationGestures(
        tap = readGesture(KEY_TAP, defaults.tap),
        longPress = readGesture(KEY_LONG_PRESS, defaults.longPress),
    )

    private fun readGesture(key: String, default: PassageUnit?): PassageUnit? {
        val stored = store.getString(key) ?: return default

        if (stored.isBlank()) return null

        return PassageUnit.entries.firstOrNull { it.name == stored } ?: default
    }

    private fun writeGestures(gestures: TranslationGestures) {
        store.putString(KEY_TAP, gestures.tap?.name.orEmpty())
        store.putString(KEY_LONG_PRESS, gestures.longPress?.name.orEmpty())
    }

    /** Off unless it says otherwise, which is what makes these off on a fresh install. */
    private fun SettingsStore.readFlag(key: String): Boolean =
        getString(key)?.equals("true", ignoreCase = true) == true

    private fun readPerProvider(prefix: String): Map<TranslationProvider, String> =
        TranslationProvider.entries.mapNotNull { provider ->
            store.getString("$prefix.${provider.name}")
                ?.takeIf { it.isNotBlank() }
                ?.let { provider to it }
        }.toMap()

    /**
     * Every provider is written, including the ones with nothing stored: a value
     * cleared has to overwrite what was there rather than leave it behind for
     * the next read to find.
     */
    private fun writePerProvider(prefix: String, values: Map<TranslationProvider, String>) {
        TranslationProvider.entries.forEach { provider ->
            store.putString("$prefix.${provider.name}", values[provider].orEmpty())
        }
    }

    private companion object {
        const val KEY_TARGET_LANGUAGE = "translation.targetLanguage"
        const val KEY_PROVIDER = "translation.provider"
        const val KEY_MODEL = "translation.model"
        const val KEY_API_KEY = "translation.apiKey"
        const val KEY_BASE_URL = "translation.baseUrl"
        const val KEY_TIMEOUT = "translation.timeoutSeconds"
        const val KEY_WORD_LOOKUP = "translation.wordLookup"
        const val KEY_GOOGLE_KEY = "translation.googleApiKey"
        const val KEY_TAP = "translation.gesture.tap"
        const val KEY_LONG_PRESS = "translation.gesture.longPress"
        const val KEY_TRANSLATE_ON_SCREEN = "translation.bulk.onScreen"
        const val KEY_TRANSLATE_AS_YOU_READ = "translation.bulk.asYouRead"
    }
}
