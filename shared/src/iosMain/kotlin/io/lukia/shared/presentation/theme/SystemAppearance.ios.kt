package io.lukia.shared.presentation.theme

import androidx.compose.runtime.Composable
import io.lukia.features.settings.domain.AppTheme

/**
 * Nothing to tell iOS.
 *
 * The launch screen is a storyboard rendered before the app is running, from a
 * colour the system resolves against the device's appearance, and there is no
 * API that lets a process change what was drawn before it started. `UIWindow`'s
 * `overrideUserInterfaceStyle` reaches the app's own UIKit views, which on a
 * Compose app is nothing anybody sees.
 */
@Composable
actual fun SystemAppearanceEffect(theme: AppTheme) = Unit
