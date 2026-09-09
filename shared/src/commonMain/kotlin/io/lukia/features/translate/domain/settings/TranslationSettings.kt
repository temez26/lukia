package io.lukia.features.translate.domain.settings

import io.lukia.features.translate.domain.BulkTranslation
import io.lukia.features.translate.domain.lookup.WordSite
import io.lukia.features.translate.domain.provider.TranslationProvider
import io.lukia.features.translate.domain.provider.isAllowedTranslationEndpoint

/**
 * What translating is set to do, as opposed to the reader's own presentation
 * settings or the app's theme.
 *
 * Its own value rather than a field on the app's settings, so this feature's
 * configuration stays inside the feature - which also lets the reader read it
 * without depending on the settings screen that writes it.
 *
 * Kept per provider rather than as one set of fields the current one owns:
 * somebody comparing a local model against a hosted one switches back and forth
 * repeatedly, and clearing the key each time would make that unbearable. So a
 * provider remembers its own key, model and address whether or not it is in use.
 */
data class TranslationSettings(
    val targetLanguage: TargetLanguage = TargetLanguage.Default,
    /**
     * Whether the reader wants a model involved in any of this at all.
     *
     * One switch over everything on the AI translation page, off until asked
     * for. Turned off, Lukia is the app it is without a service: a tap looks a
     * word up in a free dictionary, a hold opens what else it means, and nothing
     * anywhere offers to translate a sentence, spend a screenful of somebody's
     * credit, or ask for a key.
     *
     * It gates [isConfigured] rather than sitting beside it, so nothing further
     * on has to remember to ask both. What is stored under it - key, model,
     * address, the units a gesture was raised to - is kept as the reader left it
     * and comes back the moment they turn it on again.
     */
    val aiTranslation: Boolean = false,
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
     * Where the reader is sent when a line over the word is not enough. Not per
     * provider for the same reason as above, and not part of [wordLookup]
     * either: one chooses who answers inside the book and this chooses where the
     * reader goes to read more - see [WordSite].
     */
    val wordSite: WordSite = WordSite.Default,
    /**
     * The reader's own address, which only [WordSite.Custom] has one of.
     *
     * Not private, unlike the values above it: those are per provider and are
     * read through accessors that pick the current one, while this is one value
     * that means the same thing to everybody - and unlike a key, there is nothing
     * about it worth keeping out of reach.
     */
    val customWordSiteUrl: String? = null,
    /**
     * The Cloud Translation key, when the reader has one. Kept apart from the
     * per-provider keys above because Google is not one of them - it never
     * answers about a sentence - and giving it a slot in that map would mean
     * offering it as a translator everywhere else.
     */
    private val googleKey: String? = null,
    val gestures: TranslationGestures = TranslationGestures.Default,
    /**
     * Whether the translator's asides are set under the paragraph they belong
     * to - a remark about an ambiguous word, a dictionary's other senses.
     *
     * Off, with everything else translating does. They used to be on because
     * they were the only place those senses could be read; holding a word now
     * opens every sense at length and out of the way of the prose - see
     * [io.lukia.features.reader.presentation.translation.WordInquiry].
     *
     * Never covers a failure. A passage marked as translated with nothing to
     * show and no reason given is a reader wondering what happened, and that is
     * not an aside about the text.
     */
    val notes: Boolean = false,
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
     * Held apart from every other setting, in whatever the platform offers for
     * a secret - the keystore on Android, the keychain on iOS, an owner-only
     * file on the desktop. See
     * [io.lukia.core.domain.storage.SecretStore] for what each of those is and
     * is not worth.
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
     * The address to open a word at, before the word is written into it, or null
     * when the reader has chosen [WordSite.Custom] and not yet said where.
     *
     * Resolved here rather than at the button, so that "which site" and "the
     * address of a custom one" are one answer to whoever asks. See
     * [io.lukia.features.translate.domain.lookup.urlFor] for the other half.
     */
    val wordSiteUrlTemplate: String?
        get() = when (wordSite) {
            WordSite.Custom -> customWordSiteUrl?.trim()?.takeIf { it.isNotEmpty() }
            else -> wordSite.urlTemplate
        }

    /**
     * True once there is enough here to actually ask for a translation.
     *
     * About the translation service and nothing else, which is to say about the
     * part of this that runs on a model. A whole sentence has nowhere else to
     * go - see [io.lukia.features.translate.infrastructure.WordLookupTranslator]
     * - so this is what the expensive controls are offered on.
     *
     * A word is never waiting on it. Both of [WordLookup]'s answers are web APIs
     * that need nothing set up, so pointing at one word works on an install
     * nobody has configured, and nothing in the reader asks this before offering
     * the language or the gestures.
     */
    val isConfigured: Boolean
        get() = aiTranslation && (!provider.requiresKey || apiKey != null) && isEndpointAllowed

    /**
     * The two expensive ways of translating, with any the reader has turned on
     * taken back off while there is nothing that could carry them out.
     *
     * Both work a paragraph at a time, and a paragraph has nowhere to go but the
     * translation service. Left as the reader set them, a fresh install with a
     * dictionary and no service would offer a button whose only outcome is a
     * failure - and would go on offering it for as long as the flag sat in the
     * settings file.
     */
    val offeredBulkTranslation: BulkTranslation
        get() = if (isConfigured) bulkTranslation else BulkTranslation.Default

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

    fun withWordSite(site: WordSite): TranslationSettings = copy(wordSite = site)

    fun withCustomWordSiteUrl(url: String): TranslationSettings =
        copy(customWordSiteUrl = url.takeIf { it.isNotBlank() })

    fun withGoogleApiKey(key: String): TranslationSettings =
        copy(googleKey = key.takeIf { it.isNotBlank() })

    fun withGestures(gestures: TranslationGestures): TranslationSettings =
        copy(gestures = gestures)

    fun withNotes(notes: Boolean): TranslationSettings = copy(notes = notes)

    fun withAiTranslation(on: Boolean): TranslationSettings = copy(aiTranslation = on)

    fun withBulkTranslation(bulk: BulkTranslation): TranslationSettings =
        copy(bulkTranslation = bulk)

    /**
     * What is stored for a provider that is not the one currently chosen - which
     * is the whole point of keeping these per provider, and is checked by the
     * repository's tests.
     *
     * The model, the key and the address had accessors of this shape too. They
     * were read by nothing: everywhere in the app that wants one of those wants
     * the current provider's, which is what the properties above give.
     */
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
            aiTranslation: Boolean = false,
            provider: TranslationProvider,
            models: Map<TranslationProvider, String>,
            keys: Map<TranslationProvider, String>,
            baseUrls: Map<TranslationProvider, String>,
            timeouts: Map<TranslationProvider, Int> = emptyMap(),
            wordLookup: WordLookup = WordLookup.Default,
            wordSite: WordSite = WordSite.Default,
            customWordSiteUrl: String? = null,
            googleKey: String? = null,
            gestures: TranslationGestures = TranslationGestures.Default,
            notes: Boolean = false,
            bulkTranslation: BulkTranslation = BulkTranslation.Default,
        ) = TranslationSettings(
            targetLanguage = targetLanguage,
            aiTranslation = aiTranslation,
            provider = provider,
            models = models,
            keys = keys,
            baseUrls = baseUrls,
            timeouts = timeouts,
            wordLookup = wordLookup,
            wordSite = wordSite,
            customWordSiteUrl = customWordSiteUrl,
            googleKey = googleKey,
            gestures = gestures,
            notes = notes,
            bulkTranslation = bulkTranslation,
        )
    }

    /** Every provider's stored value, for whatever writes them back to disk. */
    fun storedModels(): Map<TranslationProvider, String> = models

    fun storedKeys(): Map<TranslationProvider, String> = keys

    fun storedBaseUrls(): Map<TranslationProvider, String> = baseUrls

    fun storedTimeouts(): Map<TranslationProvider, Int> = timeouts
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
