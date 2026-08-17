package io.lukia.features.translate.domain

/**
 * What translating is set to do, as opposed to the reader's own presentation
 * settings or the app's theme.
 *
 * Its own value rather than a field on the app's settings, so that everything
 * this feature needs to be configured stays inside the feature - which is also
 * what lets the reader read it without depending on the settings screen that
 * writes it.
 *
 * Everything a provider needs is kept per provider rather than as one set of
 * fields the current one owns. Somebody comparing a local model against a
 * hosted one switches back and forth repeatedly, and a design that cleared the
 * key each time would make that unbearable - so a provider remembers its own
 * key, its own model and its own address whether or not it is the one in use.
 */
data class TranslationSettings(
    val targetLanguage: TargetLanguage = TargetLanguage.Default,
    val provider: TranslationProvider = TranslationProvider.Default,
    private val models: Map<TranslationProvider, String> = emptyMap(),
    private val keys: Map<TranslationProvider, String> = emptyMap(),
    private val baseUrls: Map<TranslationProvider, String> = emptyMap()
) {

    /** What the current provider is asked for, or what it offers by default. */
    val model: String
        get() = models.entryFor(provider) ?: provider.defaultModel

    /**
     * The reader's own key for the current provider. Null when they have not
     * given one, which for a local server is the ordinary case.
     *
     * Stored as it is typed, in the same place as every other preference -
     * which on each platform is a file or a preferences store readable by
     * anything running as the same user. That is honest for a key the reader
     * pasted in themselves and can revoke, and it is not where a key belongs
     * long term: the platform keychains are, and moving it there changes how
     * this one property is read and nothing else.
     */
    val apiKey: String?
        get() = keys.entryFor(provider)

    /** Where the current provider is reached. */
    val baseUrl: String
        get() = baseUrls.entryFor(provider) ?: provider.defaultBaseUrl

    /** True once there is enough here to actually ask for a translation. */
    val isConfigured: Boolean
        get() = (!provider.requiresKey || apiKey != null) && isEndpointAllowed

    /**
     * False only when the reader has pointed this at a public address over
     * plain HTTP, which sends a passage out of their book across the internet
     * in the clear. See [isAllowedTranslationEndpoint].
     */
    val isEndpointAllowed: Boolean
        get() = isAllowedTranslationEndpoint(baseUrl)

    fun withModel(model: String): TranslationSettings =
        copy(models = models.replacing(provider, model))

    fun withApiKey(key: String): TranslationSettings =
        copy(keys = keys.replacing(provider, key))

    fun withBaseUrl(url: String): TranslationSettings =
        copy(baseUrls = baseUrls.replacing(provider, url))

    /** What is stored for a provider that is not the one currently chosen. */
    fun modelFor(provider: TranslationProvider): String =
        models.entryFor(provider) ?: provider.defaultModel

    fun apiKeyFor(provider: TranslationProvider): String? = keys.entryFor(provider)

    fun baseUrlFor(provider: TranslationProvider): String =
        baseUrls.entryFor(provider) ?: provider.defaultBaseUrl

    companion object {

        /**
         * Rebuilds the stored values, for whatever read them back off the disk.
         * Kept here so the maps themselves stay private and every reader of them
         * goes through the accessors above.
         */
        fun stored(
            targetLanguage: TargetLanguage,
            provider: TranslationProvider,
            models: Map<TranslationProvider, String>,
            keys: Map<TranslationProvider, String>,
            baseUrls: Map<TranslationProvider, String>
        ) = TranslationSettings(targetLanguage, provider, models, keys, baseUrls)
    }

    /** Every provider's stored value, for whatever writes them back to disk. */
    fun storedModels(): Map<TranslationProvider, String> = models

    fun storedKeys(): Map<TranslationProvider, String> = keys

    fun storedBaseUrls(): Map<TranslationProvider, String> = baseUrls
}

/** Blank and absent are the same thing: a field cleared to nothing is not a value. */
private fun Map<TranslationProvider, String>.entryFor(provider: TranslationProvider): String? =
    this[provider]?.trim()?.takeIf { it.isNotEmpty() }

private fun Map<TranslationProvider, String>.replacing(
    provider: TranslationProvider,
    value: String
): Map<TranslationProvider, String> = if (value.isBlank()) this - provider else this + (provider to value)
