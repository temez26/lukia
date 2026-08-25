package io.lukia.features.profile.domain

/**
 * What the reader's disc has on it.
 *
 * Three kinds rather than a picture and nothing else, because most people will
 * never choose a photograph for a reading app and should not be left with a grey
 * silhouette for not having. [Monogram] is what a new profile wears - it is made
 * from the name that was just typed, so it is personal before anybody has chosen
 * anything.
 */
sealed interface ProfileAvatar {

    /** The initials of the reader's own name - see [Usernames.initialsOf]. */
    data object Monogram : ProfileAvatar

    /** One of the drawn marks the app ships - see [ProfileSymbol]. */
    data class Symbol(val symbol: ProfileSymbol) : ProfileAvatar

    /**
     * A picture of the reader's own.
     *
     * The bytes are not here: they are stored apart and read asynchronously,
     * because they are the one part of a profile that cannot be answered in
     * time for the first frame - see `ProfileRepository`. This case means "use
     * the stored picture", and the disc falls back to [Monogram] while it is
     * being read, and for good if there turns out to be none.
     */
    data object Picture : ProfileAvatar
}

/**
 * The marks a reader can wear instead of their initials.
 *
 * Six, all drawn by hand in the same weight as the rest of the app's glyphs, and
 * all about reading or about the quiet around it. Not a mood board and not an
 * emoji picker: a short list somebody can take in at a glance is a choice, and a
 * grid of two hundred is a search.
 *
 * The names double as what the chooser announces each one as, which is why they
 * read as words.
 */
enum class ProfileSymbol(val title: String) {
    Book("Book"),
    Bookmark("Bookmark"),
    Feather("Feather"),
    Leaf("Leaf"),
    Moon("Moon"),
    Star("Star"),
}

/**
 * What colour the disc is.
 *
 * [Accent] is the default and is not a colour of its own: it is whatever the app
 * is already highlighting with, so a reader who has chosen a palette gets a
 * profile that belongs to it. The rest are for telling one profile from the app
 * around it - or simply for liking green.
 *
 * The same six hues the app's own accents offer, named the same way, so a reader
 * who has met them in Settings meets no new vocabulary here.
 */
enum class ProfileTint(val title: String) {
    Accent("Theme"),
    Blue("Blue"),
    Violet("Violet"),
    Green("Green"),
    Amber("Amber"),
    Rose("Rose"),
    Graphite("Graphite"),
}

/**
 * A picture the reader chose, as the bytes it was stored as.
 *
 * Held whole rather than as a path: it is a few kilobytes after shrinking, it is
 * read once per run, and a path would be one more thing that can go missing
 * between launches.
 */
class ProfilePicture(val bytes: ByteArray)
