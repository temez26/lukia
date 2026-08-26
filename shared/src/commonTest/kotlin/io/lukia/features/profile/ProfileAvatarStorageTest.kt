package io.lukia.features.profile

import io.lukia.core.domain.FakeSettingsStore
import io.lukia.features.profile.domain.ProfileAvatar
import io.lukia.features.profile.domain.ProfileSymbol
import io.lukia.features.profile.domain.ProfileTint
import io.lukia.features.profile.infrastructure.ProfileSettingsRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** What the reader's disc is set to, and what survives a restart. */
class ProfileAvatarStorageTest {

    private val store = FakeSettingsStore()
    private val pictures = FakeProfilePictureStore()
    private val repository = ProfileSettingsRepository(store, pictures)

    private fun named(name: String = "Ada") = repository.also { it.setUsername(name) }

    private fun reopened() = ProfileSettingsRepository(store, pictures)

    @Test
    fun aNewProfileWearsItsOwnInitialsInTheAppsOwnColour() {
        val profile = named().profile.value

        assertEquals(ProfileAvatar.Monogram, profile?.avatar)
        assertEquals(ProfileTint.Accent, profile?.tint)
    }

    @Test
    fun aChosenMarkIsStillThereNextTime() {
        named().setAvatar(ProfileAvatar.Symbol(ProfileSymbol.Feather))

        assertEquals(ProfileAvatar.Symbol(ProfileSymbol.Feather), reopened().profile.value?.avatar)
    }

    @Test
    fun aChosenColourIsStillThereNextTime() {
        named().setTint(ProfileTint.Green)

        assertEquals(ProfileTint.Green, reopened().profile.value?.tint)
    }

    /**
     * A mark written by a later build, or edited by hand into nonsense, falls
     * back to the initials rather than throwing on the way to the first frame.
     */
    @Test
    fun anUnreadableStoredMarkFallsBackToTheMonogram() {
        named().setAvatar(ProfileAvatar.Symbol(ProfileSymbol.Moon))
        store.putString("profile.avatar", "symbol:Anchor")

        assertEquals(ProfileAvatar.Monogram, reopened().profile.value?.avatar)
    }

    @Test
    fun thereIsNothingToDressBeforeThereIsAProfile() {
        repository.setAvatar(ProfileAvatar.Symbol(ProfileSymbol.Star))

        assertNull(repository.profile.value)
    }

    /** Choosing a picture is also choosing to wear it. */
    @Test
    fun aChosenPictureIsWornAsWellAsKept() = runTest {
        named().setPicture(PNG_PIXEL)

        assertEquals(ProfileAvatar.Picture, repository.profile.value?.avatar)
        assertNotNull(repository.picture.value)
        assertEquals(1, pictures.saves)
    }

    @Test
    fun removingAPictureFallsBackToTheMonogram() = runTest {
        named().setPicture(PNG_PIXEL)

        repository.clearPicture()

        assertEquals(ProfileAvatar.Monogram, repository.profile.value?.avatar)
        assertNull(repository.picture.value)
    }

    /**
     * Removing a picture while wearing a mark leaves the mark alone - the reader
     * took away a picture, not their choice of disc.
     */
    @Test
    fun removingAPictureLeavesAChosenMarkAlone() = runTest {
        named().setPicture(PNG_PIXEL)
        repository.setAvatar(ProfileAvatar.Symbol(ProfileSymbol.Leaf))

        repository.clearPicture()

        assertEquals(ProfileAvatar.Symbol(ProfileSymbol.Leaf), repository.profile.value?.avatar)
    }

    @Test
    fun aFileThatIsNotAnImageChangesNothing() = runTest {
        named().setPicture(ByteArray(0))

        assertEquals(ProfileAvatar.Monogram, repository.profile.value?.avatar)
        assertNull(repository.picture.value)
    }

    @Test
    fun theStoredPictureIsReadBackOnce() = runTest {
        named().setPicture(PNG_PIXEL)

        val next = reopened()
        assertNull(next.picture.value)

        next.loadPicture()

        assertNotNull(next.picture.value)
    }

    private companion object {

        /**
         * A one-pixel PNG, small enough that no platform's shrinking has
         * anything to do with it - which is the point: what is under test here
         * is what the profile does with an image, not what a decoder does.
         */
        val PNG_PIXEL = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15.toByte(), 0xC4.toByte(),
            0x89.toByte(), 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
            0x54, 0x78, 0x9C.toByte(), 0x63, 0x00, 0x01, 0x00, 0x00,
            0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4.toByte(), 0x00,
            0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(),
            0x42, 0x60, 0x82.toByte(),
        )
    }
}
