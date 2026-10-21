package io.lukia.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.savedstate.read
import io.lukia.Routes
import io.lukia.features.account.application.AccountSession
import io.lukia.features.account.domain.AccountStatus
import io.lukia.features.account.domain.ServerAccount
import io.lukia.features.settings.presentation.screens.AccountSummary
import io.lukia.features.settings.presentation.screens.SettingsArea
import io.lukia.features.settings.presentation.screens.SettingsAreaScreen
import io.lukia.features.settings.presentation.screens.SettingsHubScreen
import io.lukia.features.settings.presentation.screens.TargetLanguageScreen
import io.lukia.resources.Res
import io.lukia.resources.account_area
import io.lukia.resources.settings_account_sign_in_again
import io.lukia.resources.settings_account_signed_out_summary
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Settings on a phone: a list of the groups, each pushed as a page of its own -
 * see `SettingsHub`. [Routes.AccountSettings] lands on the Account page through
 * [Routes.SettingsPage], which its address already matches.
 */
internal fun NavGraphBuilder.phoneSettingsDestinations(navController: NavController) {
    composable(Routes.Settings.route) {
        SettingsHubScreen(
            onBack = { navController.popBackStack() },
            onOpen = { navController.navigate(Routes.SettingsPage.createRoute(it.routeName)) },
            account = rememberAccountSummary(),
        )
    }

    composable(Routes.SettingsPage.route) { entry ->
        val name = entry.arguments?.read { getStringOrNull(Routes.ARG_SETTINGS_AREA) }
        val area = SettingsArea.entries.firstOrNull { it.routeName == name }

        // Only a route written by hand could name no group; there is nothing to show.
        if (area == null) {
            LaunchedEffect(Unit) { navController.popBackStack() }
            return@composable
        }

        SettingsAreaScreen(
            area = area,
            onBack = { navController.popBackStack() },
            account = AppAccountPage,
            onPickLanguage = { navController.navigate(Routes.TargetLanguage.route) },
        )
    }

    composable(Routes.TargetLanguage.route) {
        TargetLanguageScreen(onBack = { navController.popBackStack() })
    }
}

private val SettingsArea.routeName: String get() = name.lowercase()

/** Who this device syncs as, in the two lines the top of the settings list holds. */
@Composable
private fun rememberAccountSummary(): AccountSummary {
    val status by koinInject<AccountSession>().status.collectAsState()

    return when (val current = status) {
        AccountStatus.SignedOut -> AccountSummary(
            title = stringResource(Res.string.account_area),
            detail = stringResource(Res.string.settings_account_signed_out_summary),
            initial = null,
        )

        is AccountStatus.SignedIn -> current.account.summary(detail = current.account.serverName)

        is AccountStatus.NeedsSignIn -> current.account.summary(
            detail = stringResource(Res.string.settings_account_sign_in_again),
        )
    }
}

private fun ServerAccount.summary(detail: String) = AccountSummary(
    title = username,
    detail = detail,
    initial = username.firstOrNull()?.uppercase(),
)
