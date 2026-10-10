package io.lukia.core.infrastructure

/**
 * Which desktop this is.
 *
 * Asked at runtime rather than at build time because the desktop target is one
 * binary that runs on all three, so there is no compile-time answer to have.
 *
 * Shared because several unrelated things need it - how translucent the app's
 * chrome is drawn, whether the platform's file panel can be asked for a folder,
 * and which modifier key the menu bar's shortcuts hang off - and a second copy
 * of this string comparison is a second chance to spell one of the names wrong.
 */
val isMacOs: Boolean by lazy {
    osName.contains("mac") || osName.contains("darwin")
}

internal val isWindows: Boolean by lazy { osName.contains("win") }

private val osName: String by lazy { System.getProperty("os.name").orEmpty().lowercase() }
