package io.lukia.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import io.lukia.features.account.presentation.AccountSettings
import io.lukia.features.settings.presentation.screens.AccountPage
import io.lukia.features.sync.presentation.HistoryQuestion
import io.lukia.features.sync.presentation.SyncStatusCard
import io.lukia.shared.presentation.theme.LocalLukiaDimens

/**
 * The Account page as Settings shows it: signing in, under it whether this
 * device is level with the server, and the one question syncing ever asks. Put together here, where the account, sync and
 * settings features meet, so none of them has to know the others.
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
    }
}
