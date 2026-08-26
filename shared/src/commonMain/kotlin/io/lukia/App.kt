package io.lukia

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.lukia.core.domain.AppTheme
import io.lukia.features.settings.infrastructure.AppSettingsRepository
import io.lukia.navigation.DesktopShell
import io.lukia.navigation.MobileShell
import io.lukia.shared.presentation.components.LaunchSplash
import io.lukia.shared.presentation.theme.LukiaTheme
import io.lukia.shared.presentation.theme.SystemAppearanceEffect
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

    // Not about this session but about the next one: the window the app opens
    // into is painted before any of the above has run, and this is what the
    // system consults when it picks the colour.
    SystemAppearanceEffect(appSettings.theme)

    LukiaTheme(
        darkTheme = darkTheme,
        palette = appSettings.palette,
        accent = appSettings.accent,
        motion = appSettings.motion,
    ) {
        // Inside the theme rather than around it: the opening is drawn on the
        // reader's own background and highlighted with the reader's own accent,
        // so the last frame of it and the first frame of the app are the same
        // colours.
        LaunchSplash {
            // Straight into the app. There is nobody to introduce and nothing to
            // ask for - the library, the positions in it and every setting are
            // already on this device - so the first thing anybody sees is their
            // own shelf rather than a form standing in front of it.
            if (mobile) MobileShell() else DesktopShell()
        }
    }
}
