package io.lukia.features.account.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.lukia.features.settings.presentation.screens.SettingsSection
import io.lukia.features.settings.presentation.screens.SettingsToggle
import io.lukia.resources.Res
import io.lukia.resources.account_admin_allow_sign_up
import io.lukia.resources.account_admin_allow_sign_up_detail
import io.lukia.resources.account_admin_title
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock

/**
 * What the server's admin can change about it. Draws nothing for anyone else:
 * the server would refuse them, and a switch that can only fail is worse than
 * none.
 */
@Composable
fun AdministrationSettings(
    groupColor: Color,
    modifier: Modifier = Modifier,
    viewModel: AdministrationViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isAdmin) { if (state.isAdmin) viewModel.load() }

    if (state.isAdmin) {
        AdministrationContent(
            state = state,
            onSignUpOpenChange = viewModel::setSignUpOpen,
            today = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date },
            groupColor = groupColor,
            modifier = modifier,
        )
    }
}

@Composable
internal fun AdministrationContent(
    state: AdministrationUiState,
    onSignUpOpenChange: (Boolean) -> Unit,
    /** What "last read" counts back from. */
    today: LocalDate,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SettingsSection(
            title = stringResource(Res.string.account_admin_title),
            groupColor = groupColor,
        ) {
            ServerControls(state, onSignUpOpenChange)
        }

        state.people?.let { people ->
            PeopleSection(
                people = people,
                today = today,
                groupColor = groupColor,
                modifier = Modifier.padding(top = LocalLukiaDimens.current.sectionSpacing),
            )
        }
    }
}

@Composable
private fun ServerControls(state: AdministrationUiState, onSignUpOpenChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        // Not drawn until the server has said: a switch shown off and then
        // jumping on would look as if the reader had turned it on.
        state.signUpOpen?.let { open ->
            SettingsToggle(
                label = stringResource(Res.string.account_admin_allow_sign_up),
                checked = open,
                onChange = onSignUpOpenChange,
                detail = stringResource(Res.string.account_admin_allow_sign_up_detail),
            )
        }

        state.problem?.let { problem ->
            Text(
                text = problem.displayMessage(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
