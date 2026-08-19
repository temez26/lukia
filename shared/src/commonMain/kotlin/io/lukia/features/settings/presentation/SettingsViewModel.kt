package io.lukia.features.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.lukia.core.domain.AppError
import io.lukia.core.domain.runCatchingApp
import io.lukia.core.domain.toAppError
import io.lukia.features.settings.domain.AppSettings
import io.lukia.features.settings.domain.AppTheme
import io.lukia.features.settings.infrastructure.AppSettingsRepository
import io.lukia.features.translate.application.TranslatePassageUseCase
import io.lukia.features.translate.domain.BulkTranslation
import io.lukia.features.translate.domain.ModelCatalogue
import io.lukia.features.translate.domain.ModelWarmUp
import io.lukia.features.translate.domain.PassageUnit
import io.lukia.features.translate.domain.TargetLanguage
import io.lukia.features.translate.domain.TranslationContext
import io.lukia.features.translate.domain.TranslationGestures
import io.lukia.features.translate.domain.TranslationProvider
import io.lukia.features.translate.domain.TranslationRequest
import io.lukia.features.translate.domain.TranslationSettings
import io.lukia.features.translate.domain.WordLookup
import io.lukia.features.translate.infrastructure.TranslationSettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    /** Carries what came back, so the reader can see it really translated. */
    data class Worked(val sample: String) : ConnectionTest

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
    private val translation: TranslationSettingsRepository,
    private val translatePassage: TranslatePassageUseCase,
    private val catalogue: ModelCatalogue,
    private val warmUp: ModelWarmUp,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings

    val translationSettings: StateFlow<TranslationSettings> = translation.settings

    private val _connectionTest = MutableStateFlow<ConnectionTest>(ConnectionTest.Untried)
    val connectionTest: StateFlow<ConnectionTest> = _connectionTest.asStateFlow()

    private val _availableModels = MutableStateFlow<AvailableModels>(AvailableModels.Unknown)
    val availableModels: StateFlow<AvailableModels> = _availableModels.asStateFlow()

    private val _modelReadiness = MutableStateFlow<ModelReadiness>(ModelReadiness.Cold)
    val modelReadiness: StateFlow<ModelReadiness> = _modelReadiness.asStateFlow()

    private var warming: Job? = null

    fun setTheme(theme: AppTheme) {
        repository.update(repository.settings.value.copy(theme = theme))
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
        _connectionTest.value = ConnectionTest.Untried
        _availableModels.value = AvailableModels.Unknown
        stopWarmingUp()
        translation.update(translation.settings.value.copy(provider = provider))

        // Asked for straight away when the reader picks a server of their own,
        // because that is the moment they need the list - and because the name
        // of a locally loaded model is not something anybody recalls exactly.
        if (provider.hasConfigurableEndpoint) loadAvailableModels()
    }

    fun setModel(model: String) = edit { withModel(model) }

    /**
     * Picking one of the models the server offers, as opposed to typing a name.
     *
     * The difference matters because the name is also a free text field, and a
     * reader is halfway through typing for most of the characters in it.
     * Choosing from the list is the one moment they have settled on a model, so
     * it is the only one worth making the server load it - see [loadChosenModel].
     */
    fun chooseModel(model: String) {
        setModel(model)

        if (translation.settings.value.provider.hasConfigurableEndpoint) loadChosenModel()
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

    /** What a tap asks for, or nothing at all when the reader turns it off. */
    fun setTapGesture(unit: PassageUnit?) = setGestures { copy(tap = unit) }

    fun setLongPressGesture(unit: PassageUnit?) = setGestures { copy(longPress = unit) }

    /**
     * Whether the reader is offered translating a screenful at a time, and
     * whether it keeps going as they scroll. See [BulkTranslation] for why these
     * are off until asked for.
     */
    fun setTranslateOnScreen(enabled: Boolean) = setBulk { copy(onScreen = enabled) }

    fun setTranslateAsYouRead(enabled: Boolean) = setBulk { copy(asYouRead = enabled) }

    private fun setBulk(change: BulkTranslation.() -> BulkTranslation) {
        val current = translation.settings.value

        translation.update(current.withBulkTranslation(current.bulkTranslation.change()))
    }

    private fun setGestures(change: TranslationGestures.() -> TranslationGestures) {
        val current = translation.settings.value

        translation.update(current.withGestures(current.gestures.change()))
    }

    fun setApiKey(key: String) = edit { withApiKey(key) }

    fun setBaseUrl(url: String) = edit { withBaseUrl(url) }

    /**
     * Asks the configured server what it can run.
     *
     * Explicit rather than fired as the address is typed: every keystroke in
     * that field would otherwise be a request to a half-finished address, and
     * most of them to a machine that is not there.
     */
    fun loadAvailableModels() {
        if (_availableModels.value == AvailableModels.Loading) return

        _availableModels.value = AvailableModels.Loading

        viewModelScope.launch {
            _availableModels.value = runCatchingApp { catalogue.models() }.fold(
                onSuccess = { AvailableModels.Loaded(it) },
                onFailure = { AvailableModels.Failed(it.toAppError()) },
            )
        }
    }

    /**
     * Asks the server to have the chosen model loaded, and shows how that goes.
     *
     * Also what the reader taps to try again after typing a model name by hand,
     * which is the other way of ending up with one that is not loaded.
     *
     * Every trigger of this replaces the last: a reader running down a list of
     * models leaves one request per model behind them otherwise, each holding a
     * server hostage while it loads something already abandoned.
     */
    fun loadChosenModel() {
        stopWarmingUp()

        _modelReadiness.value = ModelReadiness.Loading

        warming = viewModelScope.launch {
            _modelReadiness.value = runCatchingApp { warmUp.warmUp() }.fold(
                onSuccess = { ModelReadiness.Ready },
                onFailure = { ModelReadiness.Failed(it.toAppError()) },
            )
        }
    }

    private fun stopWarmingUp() {
        warming?.cancel()
        warming = null
        _modelReadiness.value = ModelReadiness.Cold
    }

    /**
     * Translates one short sentence and reports what happened.
     *
     * A real translation rather than a ping, because everything that can be
     * wrong here is downstream of connecting: a key that is refused, a model
     * name the server does not have, a local server that answers but ignores
     * the shape it was asked for. Only asking for a translation finds those.
     */
    fun testConnection() {
        if (_connectionTest.value == ConnectionTest.Trying) return

        _connectionTest.value = ConnectionTest.Trying

        viewModelScope.launch {
            val request = TranslationRequest(
                text = SAMPLE_SENTENCE,
                targetLanguage = translation.settings.value.targetLanguage,
                context = TranslationContext.None,
            )

            _connectionTest.value = translatePassage(request).fold(
                onSuccess = { ConnectionTest.Worked(it.text) },
                onFailure = { ConnectionTest.Failed(it.toAppError()) },
            )
        }
    }

    /**
     * Any edit invalidates what the last test proved. Leaving a tick beside a
     * key that has since been retyped is worse than showing nothing.
     */
    private fun edit(change: TranslationSettings.() -> TranslationSettings) {
        _connectionTest.value = ConnectionTest.Untried
        translation.update(translation.settings.value.change())
    }

    /** Editing the address invalidates a catalogue read off the old one. */
    fun onBaseUrlEdited(url: String) {
        _availableModels.value = AvailableModels.Unknown
        setBaseUrl(url)
    }

    private companion object {

        /**
         * Short, ordinary, and nothing like a test string: what comes back is
         * shown to the reader, and a recognisable sentence is easier to judge
         * than a word nobody would ever translate.
         */
        const val SAMPLE_SENTENCE = "The sea was calm that morning."
    }
}
