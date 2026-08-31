package io.lukia.features.translate.domain.settings

import io.lukia.features.translate.domain.BulkTranslation
import io.lukia.features.translate.domain.provider.TranslationProvider
import io.lukia.features.translate.domain.provider.isAllowedTranslationEndpoint
import io.lukia.features.translate.domain.text.WordLookup

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
    private val baseUrls: Map<TranslationProvider, String> = emptyMap(),
    private val timeouts: Map<TranslationProvider, Int> = emptyMap(),
    /**
     * What answers when the reader asks about a single word. Not per provider:
     * it is a choice between providers, not a setting one of them has.
     */
    val wordLookup: WordLookup = WordLookup.Default,
    /**
     * The Cloud Translation key, when the reader has one. Kept apart from the
     * per-provider keys above because Google is not one of them - it never
     * answers about a sentence - and giving it a slot in that map would mean
     * offering it as a translator everywhere else.
     */
    private val googleKey: String? = null,
    val gestures: TranslationGestures = TranslationGestures.Default,
    /**
     * Whether the reader has asked for the two ways of translating more of the
     * book than they pointed at. Off until they do - see [BulkTranslation].
     */
    val bulkTranslation: BulkTranslation = BulkTranslation.Default,
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

    /**
     * How long the current provider may go silent before the wait is given up
     * on. See [TranslationProvider.defaultTimeoutSeconds] for why this is a
     * measure of silence rather than of how long the whole answer took.
     *
     * Editable because no default can be right for a server the reader chose:
     * the same model is seconds on a machine with the memory for it and minutes
     * on one that is swapping.
     */
    val timeoutSeconds: Int
        get() = timeouts.timeoutFor(provider) ?: provider.defaultTimeoutSeconds

    /**
     * The reader's own Google key, or null when they have not given one - in
     * which case Google is asked over its public endpoint instead.
     */
    val googleApiKey: String?
        get() = googleKey?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Whether a single word goes somewhere other than the translator.
     *
     * The one thing that can be set up without setting up a translator at all:
     * a dictionary needs nothing, and Google needs nothing until the reader
     * wants the supported API.
     */
    val looksUpWordsElsewhere: Boolean
        get() = wordLookup != WordLookup.Translator

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

    /**
     * Clamped rather than rejected: this arrives from a text field, and a
     * reader who typed a zero meant "quickly", not "give up before asking".
     */
    fun withTimeoutSeconds(seconds: Int): TranslationSettings =
        copy(timeouts = timeouts + (provider to seconds.coerceIn(TimeoutSecondsRange)))

    fun withWordLookup(lookup: WordLookup): TranslationSettings = copy(wordLookup = lookup)

    fun withGoogleApiKey(key: String): TranslationSettings =
        copy(googleKey = key.takeIf { it.isNotBlank() })

    fun withGestures(gestures: TranslationGestures): TranslationSettings =
        copy(gestures = gestures)

    fun withBulkTranslation(bulk: BulkTranslation): TranslationSettings =
        copy(bulkTranslation = bulk)

    /** What is stored for a provider that is not the one currently chosen. */
    fun modelFor(provider: TranslationProvider): String =
        models.entryFor(provider) ?: provider.defaultModel

    fun apiKeyFor(provider: TranslationProvider): String? = keys.entryFor(provider)

    fun baseUrlFor(provider: TranslationProvider): String =
        baseUrls.entryFor(provider) ?: provider.defaultBaseUrl

    fun timeoutSecondsFor(provider: TranslationProvider): Int =
        timeouts.timeoutFor(provider) ?: provider.defaultTimeoutSeconds

    companion object {

        /**
         * What the reader is allowed to set the wait to.
         *
         * The floor is short enough to be impatient with and long enough that a
         * hosted service on a slow connection still gets a word in. The ceiling
         * is an hour, which is past any model load on any machine and short of a
         * request that would outlive the reading session it belongs to.
         */
        val TimeoutSecondsRange = 5..3600

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
            baseUrls: Map<TranslationProvider, String>,
            timeouts: Map<TranslationProvider, Int> = emptyMap(),
            wordLookup: WordLookup = WordLookup.Default,
            googleKey: String? = null,
            gestures: TranslationGestures = TranslationGestures.Default,
            bulkTranslation: BulkTranslation = BulkTranslation.Default,
        ) = TranslationSettings(
            targetLanguage = targetLanguage,
            provider = provider,
            models = models,
            keys = keys,
            baseUrls = baseUrls,
            timeouts = timeouts,
            wordLookup = wordLookup,
            googleKey = googleKey,
            gestures = gestures,
            bulkTranslation = bulkTranslation,
        )
    }

    /** Every provider's stored value, for whatever writes them back to disk. */
    fun storedModels(): Map<TranslationProvider, String> = models

    fun storedKeys(): Map<TranslationProvider, String> = keys

    fun storedBaseUrls(): Map<TranslationProvider, String> = baseUrls

    fun storedTimeouts(): Map<TranslationProvider, Int> = timeouts

    fun storedGoogleKey(): String = googleKey.orEmpty()
}

/** Blank and absent are the same thing: a field cleared to nothing is not a value. */
private fun Map<TranslationProvider, String>.entryFor(provider: TranslationProvider): String? =
    this[provider]?.trim()?.takeIf { it.isNotEmpty() }

/** A stored wait outside what can be set is a value from nowhere, not a setting. */
private fun Map<TranslationProvider, Int>.timeoutFor(provider: TranslationProvider): Int? =
    this[provider]?.takeIf { it in TranslationSettings.TimeoutSecondsRange }

private fun Map<TranslationProvider, String>.replacing(
    provider: TranslationProvider,
    value: String,
): Map<TranslationProvider, String> = if (value.isBlank()) this - provider else this + (provider to value)
