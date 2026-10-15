package io.lukia.features.sync.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.lukia.features.settings.presentation.screens.SettingsSection
import io.lukia.resources.Res
import io.lukia.resources.history_add
import io.lukia.resources.history_keep
import io.lukia.resources.history_question
import io.lukia.resources.history_title
import io.lukia.shared.presentation.components.controls.ButtonVariant
import io.lukia.shared.presentation.components.controls.LukiaButton
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Asks, once, whether the reading this device recorded before it was signed in
 * goes to the account's statistics. Shown under the Account page while the
 * question is open, and nowhere at all once it is answered - or when there was
 * nothing to ask about.
 */
@Composable
fun HistoryQuestion(groupColor: Color, modifier: Modifier = Modifier, viewModel: HistoryViewModel = koinViewModel()) {
    val asking by viewModel.asking.collectAsState()

    if (asking) {
        HistoryQuestionContent(onAnswer = viewModel::answer, groupColor = groupColor, modifier = modifier)
    }
}

@Composable
internal fun HistoryQuestionContent(
    onAnswer: (addToAccount: Boolean) -> Unit,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    SettingsSection(title = stringResource(Res.string.history_title), groupColor = groupColor, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            Text(text = stringResource(Res.string.history_question), style = MaterialTheme.typography.bodyMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                LukiaButton(label = stringResource(Res.string.history_add), onClick = { onAnswer(true) })
                LukiaButton(
                    label = stringResource(Res.string.history_keep),
                    onClick = { onAnswer(false) },
                    variant = ButtonVariant.Secondary,
                )
            }
        }
    }
}
