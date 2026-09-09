package io.lukia.features.settings.presentation

import io.lukia.core.domain.runCatchingApp
import io.lukia.core.domain.toAppError
import io.lukia.features.translate.application.TranslatePassageUseCase
import io.lukia.features.translate.domain.TranslationContext
import io.lukia.features.translate.domain.TranslationRequest
import io.lukia.features.translate.domain.provider.ModelCatalogue
import io.lukia.features.translate.domain.provider.ModelWarmUp
import io.lukia.features.translate.infrastructure.TranslationSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Asking the translation service whether it is there, kept beside the settings
 * rather than inside them.
 *
 * Everything here is a question put to somebody else's machine - what models it
 * has, whether it will load one, whether it can translate a sentence - and none
 * is a setting. Gathered as
 * [io.lukia.features.reader.presentation.translation.ReaderTranslations] is
 * beside `ReaderViewModel`: the view model stays about what the reader changes,
 * and everything that can hang, fail or be retried is in one place.
 *
 * Only ever asked about the provider the reader has chosen. Each of these reads
 * the settings at the moment it runs, so a probe started after a key was pasted
 * in tests the key that is there now.
 */
class ProviderProbe(
    private val settings: TranslationSettingsRepository,
    private val translatePassage: TranslatePassageUseCase,
    private val catalogue: ModelCatalogue,
    private val warmUp: ModelWarmUp,
    private val scope: CoroutineScope,
) {

    private val _connectionTest = MutableStateFlow<ConnectionTest>(ConnectionTest.Untried)
    val connectionTest: StateFlow<ConnectionTest> = _connectionTest.asStateFlow()

    private val _availableModels = MutableStateFlow<AvailableModels>(AvailableModels.Unknown)
    val availableModels: StateFlow<AvailableModels> = _availableModels.asStateFlow()

    private val _modelReadiness = MutableStateFlow<ModelReadiness>(ModelReadiness.Cold)
    val modelReadiness: StateFlow<ModelReadiness> = _modelReadiness.asStateFlow()

    private var warming: Job? = null

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

        scope.launch {
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

        warming = scope.launch {
            _modelReadiness.value = runCatchingApp { warmUp.warmUp() }.fold(
                onSuccess = { ModelReadiness.Ready },
                onFailure = { ModelReadiness.Failed(it.toAppError()) },
            )
        }
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

        scope.launch {
            val request = TranslationRequest(
                text = SAMPLE_SENTENCE,
                targetLanguage = settings.settings.value.targetLanguage,
                context = TranslationContext.None,
            )

            _connectionTest.value = translatePassage(request).fold(
                onSuccess = { ConnectionTest.Worked(sent = SAMPLE_SENTENCE, sample = it.text) },
                onFailure = { ConnectionTest.Failed(it.toAppError()) },
            )
        }
    }

    /**
     * Forgets what the last test proved, and stops any model being loaded on the
     * strength of it.
     *
     * Called for every edit to the service's settings. Leaving a tick beside a
     * key that has since been retyped is worse than showing nothing.
     */
    fun forgetTheTest() {
        _connectionTest.value = ConnectionTest.Untried
    }

    /** Forgets a catalogue read off an address that has since been edited. */
    fun forgetTheCatalogue() {
        _availableModels.value = AvailableModels.Unknown
    }

    /**
     * Forgets all of it, for a reader who has changed provider.
     *
     * That is not changing a value but changing which set of values is showing,
     * so whatever was last proven about the old provider says nothing about this
     * one - including a model it was in the middle of loading.
     */
    fun forgetEverything() {
        forgetTheTest()
        forgetTheCatalogue()
        stopWarmingUp()
    }

    private fun stopWarmingUp() {
        warming?.cancel()
        warming = null
        _modelReadiness.value = ModelReadiness.Cold
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
