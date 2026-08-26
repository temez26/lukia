package io.lukia.features.profile.infrastructure

import io.lukia.core.domain.SettingsStore
import io.lukia.core.infrastructure.PersistedSettings
import io.lukia.core.infrastructure.readEnum
import io.lukia.features.profile.domain.ProfileAccount
import io.lukia.features.profile.domain.ProfileAvatar
import io.lukia.features.profile.domain.ProfilePicture
import io.lukia.features.profile.domain.ProfilePictureStore
import io.lukia.features.profile.domain.ProfileSymbol
import io.lukia.features.profile.domain.ProfileTint
import io.lukia.features.profile.domain.UserProfile
import io.lukia.features.profile.domain.Usernames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The reader's profile, live and persisted.
 *
 * Who they are and what their disc looks like are in the preferences store, for
 * the reason the app's own settings are: they are short strings that have to be
 * known before the first frame is drawn, because whether a profile exists
 * decides whether the app opens on its library or on its welcome.
 *
 * Their picture is not, and cannot be - it is kilobytes of image and lives in
 * the database. It arrives after the first frame, which is why [picture] is a
 * flow that starts empty and why every disc in the app can draw itself without
 * one. Nothing waits for it.
 *
 * Null [profile] means nobody has been through onboarding yet, which is not the
 * same as an empty name - see [UserProfile]. A single instance is shared by the
 * app, which is why it is a `single` in the dependency graph.
 *
 * Named for the family it is in rather than for the word "profile". Nothing here
 * is a database repository like [io.lukia.features.library.domain.LibraryRepository]
 * - the one part that is kept in the database is [ProfilePictureStore], which has
 * its own port and its own implementation. What is left is a settings holder, the
 * same as the app's, the reader's and the shelf's.
 */
class ProfileSettingsRepository(
    private val store: SettingsStore,
    private val pictures: ProfilePictureStore,
) {

    // The flow starts null - see [profile] - but nothing ever puts it back to
    // null, so there is no "no profile" for [save] to have to write.
    private val persisted = PersistedSettings<UserProfile?>(load()) { it?.let(::save) }

    /** Null until the reader has been through onboarding. */
    val profile: StateFlow<UserProfile?> = persisted.state

    private val _picture = MutableStateFlow<ProfilePicture?>(null)

    /**
     * The reader's own picture, once it has been read - see [loadPicture].
     *
     * Null while it is still being read and null for the many readers who never
     * choose one, which the disc treats the same way: it draws the monogram or
     * the chosen mark, and the picture replaces it when and if it arrives.
     */
    val picture: StateFlow<ProfilePicture?> = _picture.asStateFlow()

    /**
     * Names the reader, both for the profile they are creating on their first
     * run and for the one they are renaming afterwards.
     *
     * One method for the two because they are one write. What differs between
     * them is only what was there before, which the caller already knows and the
     * store does not need to be told twice.
     *
     * A name that cannot be used is refused rather than stored blank, so the
     * next launch cannot find a profile whose name is nothing.
     */
    fun setUsername(username: String) {
        val cleaned = Usernames.clean(username)

        if (Usernames.problemWith(cleaned) != null) return

        update(profile.value?.copy(username = cleaned) ?: UserProfile(cleaned))
    }

    /** Ignored before there is a profile to dress, which is onboarding's job. */
    fun setAvatar(avatar: ProfileAvatar) {
        update(profile.value?.copy(avatar = avatar) ?: return)
    }

    fun setTint(tint: ProfileTint) {
        update(profile.value?.copy(tint = tint) ?: return)
    }

    /**
     * Reads the stored picture, once.
     *
     * Called by whatever draws the reader first - a header, the profile page -
     * rather than on construction, because construction happens before the first
     * frame and this is a database read. It is cheap to ask twice: the second
     * caller finds it already loaded and does nothing.
     */
    suspend fun loadPicture() {
        if (loadedPicture) return

        loadedPicture = true
        _picture.value = pictures.picture()
    }

    /**
     * Keeps [bytes] as the reader's picture and wears it, or does nothing at all
     * if it is not an image this device can read.
     *
     * Choosing the picture also chooses to show it. A reader who has just been
     * through a file dialog has said what they want plainly enough, and leaving
     * them to then tick "use picture" would be asking twice.
     */
    suspend fun setPicture(bytes: ByteArray) {
        val stored = storedAvatar(bytes) ?: return

        pictures.save(stored)
        loadedPicture = true
        _picture.value = stored

        setAvatar(ProfileAvatar.Picture)
    }

    /**
     * Forgets the picture, and falls back to the monogram rather than to a blank
     * disc - a profile wearing nothing at all is not a state this app has.
     */
    suspend fun clearPicture() {
        pictures.clear()
        _picture.value = null

        if (profile.value?.avatar == ProfileAvatar.Picture) setAvatar(ProfileAvatar.Monogram)
    }

    private var loadedPicture = false

    private fun update(profile: UserProfile) = persisted.set(profile)

    private fun load(): UserProfile? {
        val cleaned = Usernames.clean(store.getString(KEY_USERNAME).orEmpty())

        // A name that no longer passes the rules is treated as no profile at
        // all, which sends the reader back through onboarding rather than
        // leaving them looking at a blank avatar they cannot fix.
        if (Usernames.problemWith(cleaned) != null) return null

        return UserProfile(
            username = cleaned,
            avatar = readAvatar(),
            tint = store.readEnum(KEY_TINT, ProfileTint.entries, ProfileTint.Accent),
            account = ProfileAccount.OnThisDevice,
        )
    }

    /**
     * The stored mark, falling back to the monogram - which is what a profile
     * written by a build that had no marks has, and what a stored symbol that
     * has since been dropped from the app should become rather than a crash.
     */
    private fun readAvatar(): ProfileAvatar = when (val stored = store.getString(KEY_AVATAR)) {
        null, AVATAR_MONOGRAM -> ProfileAvatar.Monogram
        AVATAR_PICTURE -> ProfileAvatar.Picture
        else ->
            ProfileSymbol.entries
                .firstOrNull { it.name == stored.removePrefix(AVATAR_SYMBOL_PREFIX) }
                ?.let(ProfileAvatar::Symbol)
                ?: ProfileAvatar.Monogram
    }

    private fun save(profile: UserProfile) {
        store.putString(KEY_USERNAME, profile.username)
        store.putString(KEY_AVATAR, profile.avatar.stored())
        store.putString(KEY_TINT, profile.tint.name)
    }

    private fun ProfileAvatar.stored(): String = when (this) {
        ProfileAvatar.Monogram -> AVATAR_MONOGRAM
        ProfileAvatar.Picture -> AVATAR_PICTURE
        is ProfileAvatar.Symbol -> "$AVATAR_SYMBOL_PREFIX${symbol.name}"
    }

    private companion object {
        const val KEY_USERNAME = "profile.username"
        const val KEY_AVATAR = "profile.avatar"
        const val KEY_TINT = "profile.tint"

        const val AVATAR_MONOGRAM = "monogram"
        const val AVATAR_PICTURE = "picture"
        const val AVATAR_SYMBOL_PREFIX = "symbol:"
    }
}
