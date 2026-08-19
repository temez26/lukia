package io.lukia.features.settings.domain

/**
 * Which set of colours the app wears.
 *
 * [System] is the default and stays the default: an app that ignores the device's
 * own choice is the one that looks wrong at night. The other two exist because
 * following the system is a preference, not a law - a reader who wants the app
 * dark on a light phone should be able to say so.
 *
 * The names double as the labels shown on the settings screen, which is why they
 * read as words rather than as constants.
 */
enum class AppTheme {
    System,
    Light,
    Dark,
}

/**
 * Settings that apply to the whole app, as opposed to the reader's own
 * presentation settings, which belong to a book being read and live in
 * [io.lukia.features.reader.domain.ReaderSettings].
 *
 * One value carried whole, so adding a setting later is a field here and a key in
 * the repository rather than a new pipe through the app.
 */
data class AppSettings(
    val theme: AppTheme = AppTheme.System,
)
