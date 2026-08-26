package io.lukia.core.domain

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
 * Which family of colours the app wears, as opposed to how light or dark it is.
 *
 * Three looks rather than a colour picker: what a reading app's surfaces are made
 * of - paper or glass, warm or cool - is a decision somebody makes once and then
 * stops thinking about, and the three here are the ones worth having. A picker
 * would let a reader build something they have to read a book through.
 *
 * Named for what they look like rather than for where they came from. Two of them
 * are modelled on other reading apps, and a reader choosing between "Readest" and
 * "Readwise" would be choosing between two words that describe nothing; a reader
 * choosing between paper and slate already knows what they are getting. [detail]
 * is the second line under each on the settings screen.
 */
enum class AppPalette(val title: String, val detail: String) {

    /** Apple's system colours: white cards on grey, true black at night. */
    Crisp("Crisp", "White cards on grey, true black at night."),

    /** Warm paper and ink, the way a book-shaped app usually looks. */
    Paper("Paper", "Warm paper and ink, easy on the eyes for long sittings."),

    /** Cool neutral greys, flatter chrome, one confident accent. */
    Slate("Slate", "Cool neutral greys and flat, quiet chrome."),
}

/**
 * The one colour the app uses to mean "this is yours": the progress along a
 * cover, the button that does the thing, the row that is chosen.
 *
 * Presets rather than a wheel, and every one of them checked against both the
 * light and the dark surfaces of all three palettes - a reader cannot pick
 * something they then cannot read.
 *
 * [Themed] is the default and is not a colour: it is whichever accent the chosen
 * palette brings with it, so picking a palette gets its whole look rather than
 * its surfaces with somebody else's highlight.
 */
enum class AppAccent(val title: String) {
    Themed("Theme"),
    Blue("Blue"),
    Violet("Violet"),
    Green("Green"),
    Amber("Amber"),
    Rose("Rose"),
    Graphite("Graphite"),
}

/**
 * How much the app moves.
 *
 * Not a switch, because "animations off" and "animations on" are not the two
 * things people actually want. What varies between readers is how much motion
 * they want to notice: one finds it the difference between an app and a
 * document, another finds it something standing between them and the page, and
 * a reader who is sensitive to movement needs the second of those to be
 * available rather than polite.
 *
 * [Standard] is the default and is what the app was designed around. The other
 * two turn the same motion down and up rather than replacing it, so an app on
 * [Calm] is still recognisably this app.
 *
 * [detail] is the second line under each on the settings screen.
 */
enum class AppMotion(val title: String, val detail: String) {

    /** For a reader who would rather the app got out of the way. */
    Calm("Calm", "Quick cross-fades. Nothing springs, slides or sparkles."),

    /** What everything here was tuned to. */
    Standard("Standard", "A short opening, and screens that move the way you sent them."),

    /** For a reader who wants to see it work. */
    Playful("Playful", "A longer opening, springier settling, and further to travel."),
}
