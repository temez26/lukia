package io.lukia.shared.presentation.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import io.lukia.core.domain.AppTheme

/**
 * Android takes the answer from API 31 onwards, which is the same release that
 * started drawing a splash screen for every app whether it asked for one or not.
 *
 * The setting is remembered by the system rather than by the app, so it is what
 * decides which of `values/` and `values-night/` the *next* launch resolves its
 * window colour from - which is the whole point of setting it here. Below 31
 * there is nowhere to put the answer, and the window follows the device.
 */
@Composable
actual fun SystemAppearanceEffect(theme: AppTheme) {
    val context = LocalContext.current

    LaunchedEffect(theme) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@LaunchedEffect

        val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
            ?: return@LaunchedEffect

        uiMode.setApplicationNightMode(
            when (theme) {
                // Not "light": custom is how the platform spells "the app has no
                // opinion", which is what following the device amounts to.
                AppTheme.System -> UiModeManager.MODE_NIGHT_CUSTOM
                AppTheme.Light -> UiModeManager.MODE_NIGHT_NO
                AppTheme.Dark -> UiModeManager.MODE_NIGHT_YES
            },
        )
    }
}
