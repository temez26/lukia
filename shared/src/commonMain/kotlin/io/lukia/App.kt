package io.lukia

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.lukia.features.settings.domain.AppTheme
import io.lukia.features.settings.infrastructure.AppSettingsRepository
import io.lukia.navigation.DesktopShell
import io.lukia.navigation.MobileShell
import io.lukia.shared.presentation.theme.LukiaTheme
import org.koin.compose.koinInject

@Composable
fun App() {
    // Injected rather than taken from a ViewModel: the theme has to be resolved
    // above the navigation host, where no destination - and so no ViewModel scope
    // - exists yet. The repository loads synchronously, so this is settled before
    // the first frame and the app never opens in the wrong colours.
    val appSettings by koinInject<AppSettingsRepository>().settings.collectAsState()

    val darkTheme = when (appSettings.theme) {
        AppTheme.System -> isSystemInDarkTheme()
        AppTheme.Light -> false
        AppTheme.Dark -> true
    }

    // Asked of the platform rather than of the window, and asked once. Which
    // navigation an app has is not a layout question: a phone turned to
    // landscape is wide enough to answer "desktop" to any width test, and losing
    // its tab bar - and the back stack under it - for the length of a rotation
    // is not a thing any phone does.
    val mobile = remember { getPlatform().isMobile }

    LukiaTheme(
        darkTheme = darkTheme,
        palette = appSettings.palette,
        accent = appSettings.accent,
    ) {
        if (mobile) MobileShell() else DesktopShell()
    }
}
