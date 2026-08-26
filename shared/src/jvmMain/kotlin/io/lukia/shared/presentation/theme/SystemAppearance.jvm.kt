package io.lukia.shared.presentation.theme

import androidx.compose.runtime.Composable
import io.lukia.core.domain.AppTheme

/**
 * Nothing to tell the desktop. A window is created by the app that owns it,
 * so there is no frame on screen before the app has drawn one.
 */
@Composable
actual fun SystemAppearanceEffect(theme: AppTheme) = Unit
