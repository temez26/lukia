package io.lukia.features.translate.infrastructure

import io.lukia.core.domain.SecretStore
import io.lukia.core.domain.SettingsStore
import io.lukia.core.infrastructure.readEnum
import io.lukia.features.translate.domain.TargetLanguage
import io.lukia.features.translate.domain.TranslationProvider
import io.lukia.features.translate.domain.TranslationSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    private val secrets: SecretStore
) {

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<TranslationSettings> = _settings.asStateFlow()

    fun update(settings: TranslationSettings) {
        if (_settings.value == settings) return

        _settings.value = settings
        save(settings)
    }

    private fun load(): TranslationSettings {
        val defaults = TranslationSettings()

        return TranslationSettings.stored(
            targetLanguage = store.readEnum(
                KEY_TARGET_LANGUAGE,
                TargetLanguage.entries,
                defaults.targetLanguage
            ),
            provider = store.readEnum(KEY_PROVIDER, TranslationProvider.entries, defaults.provider),
            models = readPerProvider(KEY_MODEL),
            keys = readKeys(),
            baseUrls = readPerProvider(KEY_BASE_URL)
        )
    }

    private fun save(settings: TranslationSettings) {
        store.putString(KEY_TARGET_LANGUAGE, settings.targetLanguage.name)
        store.putString(KEY_PROVIDER, settings.provider.name)

        writePerProvider(KEY_MODEL, settings.storedModels())
        writeKeys(settings.storedKeys())
        writePerProvider(KEY_BASE_URL, settings.storedBaseUrls())
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
    }
}
