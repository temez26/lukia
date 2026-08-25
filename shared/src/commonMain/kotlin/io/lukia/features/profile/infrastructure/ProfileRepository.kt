package io.lukia.features.profile.infrastructure

import io.lukia.core.domain.SettingsStore
import io.lukia.features.profile.domain.ProfileAccount
import io.lukia.features.profile.domain.UserProfile
import io.lukia.features.profile.domain.Usernames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The reader's profile, live and persisted.
 *
 * In the preferences store rather than in the database, and for the same reason
 * the app's own settings are: this is one short string that has to be known
 * before the first frame is drawn. Whether a profile exists decides whether the
 * app opens on its library or on its welcome, and a question answered
 * asynchronously would mean opening on one and switching to the other in front
 * of the reader.
 *
 * Null means nobody has been through onboarding yet, which is not the same as an
 * empty name - see [UserProfile]. A single instance is shared by the app, which
 * is why it is a `single` in the dependency graph.
 */
class ProfileRepository(
    private val store: SettingsStore,
) {

    private val _profile = MutableStateFlow(load())

    /** Null until the reader has been through onboarding. */
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

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

        val updated = _profile.value?.copy(username = cleaned) ?: UserProfile(cleaned)

        if (_profile.value == updated) return

        _profile.value = updated
        save(updated)
    }

    private fun load(): UserProfile? {
        val stored = store.getString(KEY_USERNAME).orEmpty()
        val cleaned = Usernames.clean(stored)

        // A name that no longer passes the rules is treated as no profile at
        // all, which sends the reader back through onboarding rather than
        // leaving them looking at a blank avatar they cannot fix.
        if (Usernames.problemWith(cleaned) != null) return null

        return UserProfile(username = cleaned, account = ProfileAccount.OnThisDevice)
    }

    private fun save(profile: UserProfile) {
        store.putString(KEY_USERNAME, profile.username)
    }

    private companion object {
        const val KEY_USERNAME = "profile.username"
    }
}
