package io.lukia.features.profile.domain

/**
 * Where the reader's own picture is kept.
 *
 * Apart from the rest of the profile because it is the one part that cannot be
 * answered synchronously - see `ProfileRepository`, which owns both and hands
 * the app one profile made of them.
 */
interface ProfilePictureStore {

    /** Null when the reader has never chosen a picture, or has since removed it. */
    suspend fun picture(): ProfilePicture?

    /**
     * Replaces whatever was there. The bytes are expected to have been shrunk
     * already - see `AvatarPictures`, which is what does the shrinking and what
     * decides that an image is too large to keep at all.
     */
    suspend fun save(picture: ProfilePicture)

    suspend fun clear()
}
