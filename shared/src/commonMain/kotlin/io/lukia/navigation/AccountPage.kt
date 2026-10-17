package io.lukia.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import io.lukia.features.account.presentation.AccountSettings
import io.lukia.features.account.presentation.AdministrationSettings
import io.lukia.features.settings.presentation.screens.AccountPage
import io.lukia.features.sync.application.SyncCoordinator
import io.lukia.features.sync.presentation.HistoryQuestion
import io.lukia.features.sync.presentation.SyncStatusCard
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * The Account page as Settings shows it: signing in, under it whether this
 * device is level with the server, the one question syncing ever asks, and -
 * for the server's admin only - looking after the server. Put together here,
 * where the account, sync and settings features meet, so none of them has to
 * know the others.
 */
internal val AppAccountPage: AccountPage = { groupColor ->
    Column {
        AccountSettings(groupColor)
        SyncStatusCard(
            groupColor = groupColor,
            modifier = Modifier.padding(top = LocalLukiaDimens.current.sectionSpacing),
        )
        HistoryQuestion(
            groupColor = groupColor,
            modifier = Modifier.padding(top = LocalLukiaDimens.current.sectionSpacing),
        )
        AdministrationSettings(
            groupColor = groupColor,
            modifier = Modifier.padding(top = LocalLukiaDimens.current.sectionSpacing),
        )
    }
}

/**
 * Starts a round with the sync server now - for "Sync with server" in the
 * library's menu. Does nothing while signed out; see `SyncCoordinator.syncNow`.
 */
@Composable
internal fun rememberStartSync(): () -> Unit {
    val coordinator = koinInject<SyncCoordinator>()
    val scope = rememberCoroutineScope()
    return remember(coordinator, scope) { { scope.launch { coordinator.syncNow() } } }
}
