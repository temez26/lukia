package io.lukia.core.domain

/**
 * Which set of colours the app wears.
 *
 * [System] is and stays the default: an app that ignores the device's own choice
 * is the one that looks wrong at night. The other two exist because following
 * the system is a preference rather than a law.
 *
 * What each is called on the settings screen lives in `ThemeChoicesDisplay.kt`:
 * a name is something the reader reads, and this file is read by the code that
 * stores the setting.
 */
enum class AppTheme {
    System,
    Light,
    Dark,
}

/**
 * Which family of colours the app wears, as opposed to how light or dark it is.
 *
 * Three looks rather than a colour picker: what a reading app's surfaces are
 * made of is decided once and then stopped thinking about, and a picker would
 * let a reader build something they have to read a book through.
 *
 * Named for what they look like rather than where they came from. Two are
 * modelled on other reading apps, and choosing between "Readest" and "Readwise"
 * is choosing between two words that describe nothing.
 */
enum class AppPalette {

    /** Apple's system colours: white cards on grey, true black at night. */
    Crisp,

    /** Warm paper and ink, the way a book-shaped app usually looks. */
    Paper,

    /** Cool neutral greys, flatter chrome, one confident accent. */
    Slate,
}

/**
 * The one colour the app uses to mean "this is yours": the progress along a
 * cover, the button that does the thing, the row that is chosen.
 *
 * Presets rather than a wheel, each checked against the light and dark surfaces
 * of all three palettes, so a reader cannot pick something they cannot read.
 *
 * [Themed] is the default and is not a colour: it is whichever accent the chosen
 * palette brings, so picking a palette gets its whole look rather than its
 * surfaces with somebody else's highlight.
 */
enum class AppAccent {
    Themed,
    Blue,
    Violet,
    Green,
    Amber,
    Rose,
    Graphite,
}

/**
 * How much the app moves.
 *
 * Not a switch, because what varies between readers is how much motion they want
 * to notice: one finds it the difference between an app and a document, another
 * finds it something standing between them and the page, and a reader sensitive
 * to movement needs that second option to be real rather than polite.
 *
 * [Standard] is the default and what the app was designed around. The other two
 * turn the same motion down and up rather than replacing it, so an app on [Calm]
 * is still recognisably this app.
 */
enum class AppMotion {

    /** For a reader who would rather the app got out of the way. */
    Calm,

    /** What everything here was tuned to. */
    Standard,

    /** For a reader who wants to see it work. */
    Playful,
}
