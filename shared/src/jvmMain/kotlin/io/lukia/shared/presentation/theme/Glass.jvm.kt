package io.lukia.shared.presentation.theme

/**
 * The desktop target is a single binary that also runs on macOS, so the Apple
 * treatment is selected at runtime rather than at compile time.
 */
actual fun platformGlassStyle(): GlassStyle {
    val osName = System.getProperty("os.name").orEmpty().lowercase()
    val isMacOs = osName.contains("mac") || osName.contains("darwin")

    return if (isMacOs) AppleGlassStyle else StandardGlassStyle
}
