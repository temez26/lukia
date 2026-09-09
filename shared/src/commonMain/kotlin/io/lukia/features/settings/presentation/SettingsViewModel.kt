package io.lukia.features.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.lukia.core.domain.AppError
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.infrastructure.ReaderSettingsRepository
import io.lukia.features.settings.domain.AppSettings
import io.lukia.features.settings.infrastructure.AppSettingsRepository
import io.lukia.features.translate.application.TranslatePassageUseCase
import io.lukia.features.translate.domain.BulkTranslation
import io.lukia.features.translate.domain.lookup.WordSite
import io.lukia.features.translate.domain.provider.ModelCatalogue
import io.lukia.features.translate.domain.provider.ModelWarmUp
import io.lukia.features.translate.domain.provider.TranslationProvider
import io.lukia.features.translate.domain.settings.TargetLanguage
import io.lukia.features.translate.domain.settings.TranslationGestures
import io.lukia.features.translate.domain.settings.TranslationSettings
import io.lukia.features.translate.domain.settings.WordLookup
import io.lukia.features.translate.infrastructure.TranslationSettingsRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * What happened the last time the reader asked whether their translator works.
 *
 * Worth having as a state of its own because setting one of these up is the
 * kind of thing that goes wrong quietly: with a hosted service a bad key shows
 * up as one failed sentence in a book, and with a server on somebody's own
 * machine misconfiguration is the ordinary first state. Finding out by tapping
 * a word mid-chapter is a poor way to learn the address was wrong.
 */
sealed interface ConnectionTest {

    data object Untried : ConnectionTest

    data object Trying : ConnectionTest

    /**
     * Carries both halves, so the reader can see it really translated.
     *
     * [sent] as well as [sample] because the row shows the pair, and a screen
     * that held its own copy of the sentence would be showing what it believed
     * was asked rather than what was.
     */
    data class Worked(val sent: String, val sample: String) : ConnectionTest

    data class Failed(val error: AppError) : ConnectionTest
}

/**
 * What the configured server said it can run, when it has been asked.
 *
 * [Unknown] is not an empty list: one means nobody has asked yet, and the other
 * would mean the server answered and had nothing. Shown differently, because
 * they call for different things from the reader.
 */
sealed interface AvailableModels {

    data object Unknown : AvailableModels

    data object Loading : AvailableModels

    data class Loaded(val models: List<String>) : AvailableModels

    data class Failed(val error: AppError) : AvailableModels
}

/**
 * Whether the chosen model is ready to answer.
 *
 * Only ever anything but [Cold] for a server the reader runs themselves. A
 * hosted service holds everything it offers loaded; a local one holds one model
 * and reads it off disk when asked for another, which is the wait this state
 * exists to make visible instead of leaving it to ambush the first tapped
 * sentence.
 */
sealed interface ModelReadiness {

    data object Cold : ModelReadiness

    data object Loading : ModelReadiness

    data object Ready : ModelReadiness

    data class Failed(val error: AppError) : ModelReadiness
}

/**
 * The settings screen's half of the app's settings.
 *
 * There is no [io.lukia.core.presentation.UiState] here and no loading: the store
 * behind this is synchronous by design, so these values are already known by the
 * time the screen is composed. A settings screen that opened on a spinner would
 * be slower than the thing it configures.
 *
 * Several dependencies rather than one, because the app's appearance, how it
 * translates, actually translating something, and what a server can be asked to
 * run are separately owned. This screen is where they happen to be edited
 * together, which is not a reason for them to live together.
 */
class SettingsViewModel(
    private val repository: AppSettingsRepository,
    private val reader: ReaderSettingsRepository,
    private val translation: TranslationSettingsRepository,
    private val translatePassage: TranslatePassageUseCase,
    private val catalogue: ModelCatalogue,
    private val warmUp: ModelWarmUp,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings

    /**
     * The same settings the reader's own panel edits, not a copy of them.
     *
     * There is one set of reader settings in the app and they were always
     * app-wide; the panel is just where they used to be reachable from. Reading
     * the repository's own flow here is what makes a change on this page show up
     * in a book that is already open.
     */
    val readerSettings: StateFlow<ReaderSettings> = reader.settings

    val translationSettings: StateFlow<TranslationSettings> = translation.settings

    /**
     * Everything this page asks of somebody else's machine - see [ProviderProbe].
     *
     * Its own thing rather than more of this class, the way `ReaderViewModel`
     * keeps translating in `ReaderTranslations`: what is left here is the
     * settings the reader changes, and what moved is everything that can hang,
     * fail or be retried.
     */
    val probe = ProviderProbe(
        settings = translation,
        translatePassage = translatePassage,
        catalogue = catalogue,
        warmUp = warmUp,
        scope = viewModelScope,
    )

    /**
     * The app's own settings whole - how light it is, its palette, its accent,
     * its motion and whether the developer settings are out - rather than a
     * setter for each.
     *
     * They are changed from one page that already holds all of them, and
     * [AppSettingsRepository] writes nothing when nothing changed. A setter per
     * field would be five ways to say the same sentence.
     */
    fun setAppSettings(settings: AppSettings) {
        repository.update(settings)
    }

    /**
     * How books are set - the page, the type, and what a PDF opens as - carried
     * whole for the same reason [setAppSettings] is.
     */
    fun setReaderSettings(settings: ReaderSettings) {
        reader.update(settings)
    }

    fun setTargetLanguage(language: TargetLanguage) {
        translation.update(translation.settings.value.copy(targetLanguage = language))
    }

    /**
     * Changing provider is not changing a value, it is changing which set of
     * values is showing - so whatever was last proven about the old one no
     * longer says anything about this one.
     */
    fun setProvider(provider: TranslationProvider) {
        probe.forgetEverything()
        translation.update(translation.settings.value.copy(provider = provider))

        // Asked for straight away when the reader picks a server of their own,
        // because that is the moment they need the list - and because the name
        // of a locally loaded model is not something anybody recalls exactly.
        if (provider.hasConfigurableEndpoint) probe.loadAvailableModels()
    }

    fun setModel(model: String) = edit { withModel(model) }

    /**
     * Picking one of the models the server offers, as opposed to typing a name.
     *
     * The difference matters because the name is also a free text field, and a
     * reader is halfway through typing for most of the characters in it.
     * Choosing from the list is the one moment they have settled on a model, so
     * it is the only one worth making the server load it - see
     * [ProviderProbe.loadChosenModel].
     */
    fun chooseModel(model: String) {
        setModel(model)

        if (translation.settings.value.provider.hasConfigurableEndpoint) probe.loadChosenModel()
    }

    /**
     * How long the current provider may go quiet before the wait is given up on.
     *
     * Worth exposing at all because no default fits every machine: the same
     * model answers in seconds where there is memory for it and minutes where
     * there is not, and only the person whose machine it is knows which they
     * have.
     */
    fun setTimeoutSeconds(seconds: Int) = edit { withTimeoutSeconds(seconds) }

    /**
     * What answers when the reader asks about one word.
     *
     * Not an [edit]: the connection test proves the translator works, and which
     * service answers about a single word says nothing about that either way.
     */
    fun setWordLookup(lookup: WordLookup) {
        translation.update(translation.settings.value.withWordLookup(lookup))
    }

    fun setGoogleApiKey(key: String) {
        translation.update(translation.settings.value.withGoogleApiKey(key))
    }

    /**
     * Where the reader is sent when a line over the word was not enough - see
     * [WordSite]. Not an [edit] for the same reason as the word lookup above:
     * nothing about somebody else's website says whether this app's translator
     * works.
     */
    fun setWordSite(site: WordSite) {
        translation.update(translation.settings.value.withWordSite(site))
    }

    fun setWordSiteUrl(url: String) {
        translation.update(translation.settings.value.withCustomWordSiteUrl(url))
    }

    /**
     * What a tap and a press-and-hold ask for, or nothing at all when the reader
     * turns one off.
     *
     * Both at once rather than one function each, because that is how the
     * reader's own panel already sets them - and two ways of writing the same
     * setting is one more than there should be.
     */
    fun setGestures(gestures: TranslationGestures) {
        translation.update(translation.settings.value.withGestures(gestures))
    }

    /**
     * Whether a model is involved in any of this at all - see
     * [io.lukia.features.translate.domain.settings.TranslationSettings.aiTranslation].
     *
     * Nothing is cleared when it goes off. The key, the model, the address and
     * the units a gesture was raised to are all still there, doing nothing, and
     * are what the reader gets back when they turn it on again.
     */
    fun setAiTranslation(on: Boolean) {
        translation.update(translation.settings.value.withAiTranslation(on))
    }

    /**
     * Whether the translator's asides are set under the paragraph they belong to.
     *
     * Not an [edit]: where a note is drawn says nothing about whether the
     * translator works.
     */
    fun setNotes(notes: Boolean) {
        translation.update(translation.settings.value.withNotes(notes))
    }

    /**
     * Whether the reader is offered translating a screenful at a time, and
     * whether it keeps going as they scroll. See [BulkTranslation] for why both
     * are off until asked for.
     *
     * Both at once rather than one function each, for the reason [setGestures]
     * takes both of its: two ways of writing one value is one more than there
     * should be.
     */
    fun setBulkTranslation(bulk: BulkTranslation) {
        translation.update(translation.settings.value.withBulkTranslation(bulk))
    }

    fun setApiKey(key: String) = edit { withApiKey(key) }

    /**
     * Private, because nothing outside should write the address without also
     * throwing away a catalogue read off the old one - which is what
     * [onBaseUrlEdited] is, and is the only way in.
     */
    private fun setBaseUrl(url: String) = edit { withBaseUrl(url) }

    /**
     * Any edit invalidates what the last test proved. Leaving a tick beside a
     * key that has since been retyped is worse than showing nothing.
     */
    private fun edit(change: TranslationSettings.() -> TranslationSettings) {
        probe.forgetTheTest()
        translation.update(translation.settings.value.change())
    }

    /** Editing the address invalidates a catalogue read off the old one. */
    fun onBaseUrlEdited(url: String) {
        probe.forgetTheCatalogue()
        setBaseUrl(url)
    }
}
