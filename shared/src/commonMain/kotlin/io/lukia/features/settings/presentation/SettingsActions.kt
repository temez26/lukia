package io.lukia.features.settings.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.lukia.features.translate.domain.PassageUnit
import io.lukia.features.translate.domain.TargetLanguage
import io.lukia.features.translate.domain.TranslationProvider
import io.lukia.features.translate.domain.WordLookup

/**
 * What the settings screen may be told about the service it is configuring.
 *
 * Three answers that only ever mean anything together - whether the translator
 * has been proved to work, what the server said it can run, and whether the
 * chosen model is loaded - and only ever to the one group that asks for them.
 * Carried as one value so that every layer between the ViewModel and that group
 * passes one parameter rather than three it does not read.
 */
data class ProviderState(
    val connectionTest: ConnectionTest = ConnectionTest.Untried,
    val availableModels: AvailableModels = AvailableModels.Unknown,
    val modelReadiness: ModelReadiness = ModelReadiness.Cold,
)

/**
 * Everything the Providers group can ask for.
 *
 * Gathered rather than passed one by one because they travel together through
 * four composables that do nothing with them, and a screen whose signature is
 * twenty callbacks is a screen that cannot be split - see the parameter limits
 * in `CODING_STYLE.md`.
 *
 * Every one defaults to doing nothing, so a caller rendering the page to look at
 * it - a preview, a test - supplies only what it means to exercise.
 */
data class ProviderActions(
    val onProviderChange: (TranslationProvider) -> Unit = {},
    val onBaseUrlChange: (String) -> Unit = {},
    val onApiKeyChange: (String) -> Unit = {},
    val onModelChange: (String) -> Unit = {},
    val onModelChoose: (String) -> Unit = {},
    val onLoadModels: () -> Unit = {},
    val onLoadChosenModel: () -> Unit = {},
    val onTimeoutChange: (Int) -> Unit = {},
    val onTestConnection: () -> Unit = {},
    val onWordLookupChange: (WordLookup) -> Unit = {},
    val onGoogleApiKeyChange: (String) -> Unit = {},
)

/** Everything the Translating group can ask for. See [ProviderActions]. */
data class TranslatingActions(
    val onTargetLanguageChange: (TargetLanguage) -> Unit = {},
    val onTapGestureChange: (PassageUnit?) -> Unit = {},
    val onLongPressGestureChange: (PassageUnit?) -> Unit = {},
    val onTranslateOnScreenChange: (Boolean) -> Unit = {},
    val onTranslateAsYouReadChange: (Boolean) -> Unit = {},
)

/**
 * The Providers group wired to the ViewModel behind it.
 *
 * Here rather than at each call site because the settings are shown two ways -
 * a screen on a phone, a panel on anything wider - and the wiring is the one
 * part of that which must not differ between them. Remembered against the
 * ViewModel so a recomposition does not rebuild eleven bound references.
 */
@Composable
fun rememberProviderActions(viewModel: SettingsViewModel): ProviderActions =
    remember(viewModel) {
        ProviderActions(
            onProviderChange = viewModel::setProvider,
            onBaseUrlChange = viewModel::onBaseUrlEdited,
            onApiKeyChange = viewModel::setApiKey,
            onModelChange = viewModel::setModel,
            onModelChoose = viewModel::chooseModel,
            onLoadModels = viewModel::loadAvailableModels,
            onLoadChosenModel = viewModel::loadChosenModel,
            onTimeoutChange = viewModel::setTimeoutSeconds,
            onTestConnection = viewModel::testConnection,
            onWordLookupChange = viewModel::setWordLookup,
            onGoogleApiKeyChange = viewModel::setGoogleApiKey,
        )
    }

/** The Translating group wired to the ViewModel. See [rememberProviderActions]. */
@Composable
fun rememberTranslatingActions(viewModel: SettingsViewModel): TranslatingActions =
    remember(viewModel) {
        TranslatingActions(
            onTargetLanguageChange = viewModel::setTargetLanguage,
            onTapGestureChange = viewModel::setTapGesture,
            onLongPressGestureChange = viewModel::setLongPressGesture,
            onTranslateOnScreenChange = viewModel::setTranslateOnScreen,
            onTranslateAsYouReadChange = viewModel::setTranslateAsYouRead,
        )
    }
