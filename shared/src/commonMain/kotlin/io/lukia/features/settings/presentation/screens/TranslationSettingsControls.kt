package io.lukia.features.settings.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.lukia.core.presentation.displayShortMessage
import io.lukia.features.settings.presentation.AvailableModels
import io.lukia.features.settings.presentation.ConnectionTest
import io.lukia.features.settings.presentation.ModelReadiness
import io.lukia.features.settings.presentation.components.SettingsTextField
import io.lukia.features.translate.domain.provider.TranslationProvider
import io.lukia.features.translate.presentation.displayName
import io.lukia.shared.presentation.components.controls.ButtonVariant
import io.lukia.shared.presentation.components.controls.ChoiceList
import io.lukia.shared.presentation.components.controls.LukiaButton
import io.lukia.shared.presentation.theme.Space

// The controls only the translation group has: picking a model off a server,
// waiting for one to load, a timeout, and testing that any of it works.

/**
 * What the reader's own server says it can run.
 *
 * Asked for rather than guessed at, because a local model's name has to match
 * exactly and is not something anybody recalls character for character. The
 * free-text field above this stays either way - a server that will not answer
 * the question can still translate perfectly well once its model is named by
 * hand.
 */
@Composable
internal fun ServerModels(
    state: AvailableModels,
    selected: String,
    onSelect: (String) -> Unit,
    onLoad: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        LukiaButton(
            label = when (state) {
                AvailableModels.Loading -> "Asking the server…"
                is AvailableModels.Loaded -> "Reload models"
                else -> "Load models from server"
            },
            onClick = onLoad,
            variant = ButtonVariant.Secondary,
            enabled = state != AvailableModels.Loading,
        )

        when (state) {
            AvailableModels.Unknown, AvailableModels.Loading -> Unit

            is AvailableModels.Loaded -> ChoiceList(
                options = state.models,
                selected = selected,
                label = { it },
                onSelect = onSelect,
            )

            is AvailableModels.Failed -> Text(
                text = state.error.displayShortMessage(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Whether the model the reader just picked is loaded and ready.
 *
 * The whole point of showing this is that the waiting has to happen somewhere.
 * A server holding a different model has to read this one off disk before it
 * can answer anything, and that is minutes for a large one - so it is spent
 * here, watched, rather than on the first sentence tapped in a book, where it
 * looks like the app has stopped.
 *
 * The button stays available once it has worked, because the reader can type a
 * model name by hand instead of picking one, and because a server restarted
 * since is a server that has forgotten.
 */
@Composable
internal fun ModelReadinessRow(
    state: ModelReadiness,
    model: String,
    onLoad: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        LukiaButton(
            label = when (state) {
                ModelReadiness.Loading -> "Loading…"
                else -> "Load this model now"
            },
            onClick = onLoad,
            variant = ButtonVariant.Secondary,
            enabled = model.isNotBlank() && state != ModelReadiness.Loading,
        )

        when (state) {
            ModelReadiness.Cold -> Unit

            ModelReadiness.Loading -> Text(
                text = "Asking the server to load $model. A large model can take a few " +
                    "minutes the first time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ModelReadiness.Ready -> Text(
                text = "$model is loaded and answering.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is ModelReadiness.Failed -> Text(
                text = state.error.displayShortMessage(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * How long this provider may go quiet before the wait is given up on.
 *
 * Silence rather than total time, which is why the number can be this large
 * without a slow answer hanging the reader: a provider still sending is never
 * given up on, and one that has stopped is given up on after this.
 *
 * The typed text is kept here rather than pushed straight into settings so that
 * a field being cleared to retype it does not momentarily mean "no wait at
 * all". What leaves this composable is only ever a number.
 */
@Composable
internal fun TimeoutField(
    provider: TranslationProvider,
    seconds: Int,
    onChange: (Int) -> Unit,
) {
    // Keyed on the provider because each keeps its own, and switching between
    // them has to show the one now in force rather than what was last typed.
    var typed by remember(provider) { mutableStateOf(seconds.toString()) }

    LabelledField(label = "Give up after") {
        SettingsTextField(
            value = typed,
            onValueChange = { entry ->
                typed = entry.filter { it.isDigit() }.take(4)
                typed.toIntOrNull()?.let(onChange)
            },
            placeholder = "${provider.defaultTimeoutSeconds} seconds",
        )
    }
}

/**
 * Proving the translator works, before a book is opened rather than during one.
 *
 * The sentence that comes back is shown rather than a tick, because "connected"
 * is not the question. A local server can answer perfectly and still return
 * prose instead of a translation, and a hosted one can accept the key and not
 * know the model - both of which look like success from any distance and are
 * obvious the moment the words are on screen.
 */
@Composable
internal fun ConnectionTestRow(
    state: ConnectionTest,
    enabled: Boolean,
    onTest: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        LukiaButton(
            label = if (state is ConnectionTest.Trying) "Testing…" else "Test translation",
            onClick = onTest,
            variant = ButtonVariant.Secondary,
            enabled = enabled && state !is ConnectionTest.Trying,
        )

        when (state) {
            ConnectionTest.Untried, ConnectionTest.Trying -> Unit

            is ConnectionTest.Worked -> Text(
                text = "\u201CThe sea was calm that morning.\u201D became \u201C${state.sample}\u201D",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is ConnectionTest.Failed -> Text(
                text = state.error.displayShortMessage(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * What is worth saying under each provider's settings - which is different for
 * each, because what can go wrong with each is different.
 */
@Composable
internal fun TranslationProvider.footnote(): String = when (this) {
    TranslationProvider.Anthropic, TranslationProvider.OpenAi ->
        "Your key is stored on this device and sent only to ${displayName()}, with the " +
            "sentence you tapped and what the book says about itself."

    TranslationProvider.Local ->
        "Nothing leaves your network. On a phone or tablet, \u201Clocalhost\u201D means the " +
            "device itself - use the computer\u2019s address on your network instead."
}
