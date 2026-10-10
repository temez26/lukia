package io.lukia.shared.presentation.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
actual fun SystemBarIcons(dark: Boolean) {
    val view = LocalView.current

    // The configuration is a key because the activity applies its edge-to-edge
    // styles again when it changes, which resets the icons to the device's.
    DisposableEffect(view, dark, LocalConfiguration.current) {
        val window = view.context.activity()?.window ?: return@DisposableEffect onDispose {}
        val bars = WindowCompat.getInsetsController(window, view)

        val statusWasDark = bars.isAppearanceLightStatusBars
        val navigationWasDark = bars.isAppearanceLightNavigationBars

        bars.isAppearanceLightStatusBars = dark
        bars.isAppearanceLightNavigationBars = dark

        onDispose {
            bars.isAppearanceLightStatusBars = statusWasDark
            bars.isAppearanceLightNavigationBars = navigationWasDark
        }
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
