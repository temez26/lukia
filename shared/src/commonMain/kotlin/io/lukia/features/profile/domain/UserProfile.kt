package io.lukia.features.profile.domain

/**
 * Who is reading.
 *
 * A username and nothing else, because nothing else is needed to read a book.
 * The app's whole library, every position in it and every setting already live
 * on the device; a profile is what puts a name and a face on that, not a licence
 * to use it. Asking for an email before the first page would be charging rent on
 * something already owned.
 *
 * That is also why there is no identifier here. A profile is addressed by being
 * the one on this device, and the day there is a second way to be addressed -
 * see [ProfileAccount] - is the day it earns a field.
 */
data class UserProfile(
    val username: String,
    val account: ProfileAccount = ProfileAccount.OnThisDevice,
) {

    /** What the avatar draws - see [Usernames.initialsOf]. */
    val initials: String get() = Usernames.initialsOf(username)
}

/**
 * Where a profile is kept, and so what it would take to get it back.
 *
 * Sealed with one case rather than left implicit, because the second case is
 * already foreseen: signing in with Google, or with anything else, adds a case
 * here and the compiler then finds every screen that has to say something
 * different about it. Nothing above this type asks "is this a local profile" -
 * they match on it - so a linked account cannot be added and quietly displayed
 * as a device-only one.
 *
 * What must not change when that day comes is [OnThisDevice] remaining the state
 * a new reader starts in. Signing in is for carrying a profile to a second
 * device, which is a thing to want later and never a thing to require first.
 */
sealed interface ProfileAccount {

    /** Kept on this device, by this app, and nowhere else. */
    data object OnThisDevice : ProfileAccount
}

/** Why a typed name cannot be used, or null when it can. */
enum class UsernameProblem {
    Empty,
    TooShort,
}

/**
 * What the app will accept as a name, and how it tidies what it is given.
 *
 * Rules rather than a free string because this is typed once, on the first
 * screen a reader ever sees, and then shown on every visit to their profile. The
 * bar is deliberately low - a name is not a login, so it needs no uniqueness, no
 * character classes and no case - and it is set here rather than in the field
 * that collects it so that onboarding and renaming cannot disagree about it.
 */
object Usernames {

    const val MinLength = 2

    /**
     * Long enough for a full name, short enough to sit on one line of a phone
     * beside an avatar. Enforced by trimming rather than by refusing, so a long
     * paste is shortened in front of the reader rather than rejected after it.
     */
    const val MaxLength = 24

    /**
     * The name as it will be stored: outer space dropped, inner runs of space
     * collapsed, and cut to [MaxLength].
     *
     * Applied as the reader types rather than on the way out, so what the field
     * shows is what the profile will hold.
     */
    fun clean(raw: String): String = raw
        .split(' ', '\t', '\n')
        .filter { it.isNotEmpty() }
        .joinToString(separator = " ")
        .take(MaxLength)

    /**
     * The monogram for a name, which is what stands in for a picture everywhere
     * the reader is shown.
     *
     * Two letters at most, from two parts at most: initials are a way of
     * recognising a name at a glance, and three of them is a monogram nobody
     * reads. A name written as one word gives one letter rather than a guess at
     * where its parts divide.
     *
     * Here rather than on [UserProfile] because onboarding draws it before there
     * is a profile to draw it from.
     */
    fun initialsOf(username: String): String = clean(username)
        .split(' ', '-', '_')
        .filter { it.isNotEmpty() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString(separator = "")
        .ifEmpty { "?" }

    /** Null when [raw] is usable as it stands, once [clean]ed. */
    fun problemWith(raw: String): UsernameProblem? {
        val cleaned = clean(raw)

        return when {
            cleaned.isEmpty() -> UsernameProblem.Empty
            cleaned.length < MinLength -> UsernameProblem.TooShort
            else -> null
        }
    }
}
