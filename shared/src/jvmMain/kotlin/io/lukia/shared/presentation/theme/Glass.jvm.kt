package io.lukia.shared.presentation.theme

import io.lukia.core.infrastructure.isMacOs

/**
 * The desktop target is a single binary that also runs on macOS, so the Apple
 * treatment is selected at runtime rather than at compile time.
 */
actual fun platformGlassStyle(): GlassStyle =
    if (isMacOs) AppleGlassStyle else StandardGlassStyle
