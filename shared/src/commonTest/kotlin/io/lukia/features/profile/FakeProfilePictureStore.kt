package io.lukia.features.profile

import io.lukia.features.profile.domain.ProfilePicture
import io.lukia.features.profile.domain.ProfilePictureStore

/**
 * One picture in memory, for the tests that only need somewhere for it to go.
 * What the real store does with SQL is covered against a real database in
 * `SqlProfilePictureStoreTest`.
 */
class FakeProfilePictureStore(
    private var stored: ProfilePicture? = null,
) : ProfilePictureStore {

    var saves = 0
        private set

    override suspend fun picture(): ProfilePicture? = stored

    override suspend fun save(picture: ProfilePicture) {
        stored = picture
        saves++
    }

    override suspend fun clear() {
        stored = null
    }
}
