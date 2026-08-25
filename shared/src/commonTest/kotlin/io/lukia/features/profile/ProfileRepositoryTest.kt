package io.lukia.features.profile

import io.lukia.core.domain.FakeSettingsStore
import io.lukia.features.profile.domain.ProfileAccount
import io.lukia.features.profile.infrastructure.ProfileRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Whether there is a reader, what they are called, and what survives a restart. */
class ProfileRepositoryTest {

    @Test
    fun thereIsNoProfileUntilOneIsMade() {
        assertNull(ProfileRepository(FakeSettingsStore(), FakeProfilePictureStore()).profile.value)
    }

    @Test
    fun namingTheReaderCreatesTheProfile() {
        val store = FakeSettingsStore()
        val repository = ProfileRepository(store, FakeProfilePictureStore())

        repository.setUsername("Ada")

        assertEquals("Ada", repository.profile.value?.username)
        assertEquals("Ada", store.values["profile.username"])
    }

    /** A profile starts on the device it was made on, and nowhere else. */
    @Test
    fun aNewProfileIsKeptOnThisDevice() {
        val repository = ProfileRepository(FakeSettingsStore(), FakeProfilePictureStore())

        repository.setUsername("Ada")

        assertEquals(ProfileAccount.OnThisDevice, repository.profile.value?.account)
    }

    @Test
    fun aStoredNameIsThereOnTheNextRun() {
        val store = FakeSettingsStore(mapOf("profile.username" to "Ada Lovelace"))

        assertEquals("Ada Lovelace", ProfileRepository(store, FakeProfilePictureStore()).profile.value?.username)
    }

    @Test
    fun renamingKeepsOneProfileRatherThanMakingASecond() {
        val store = FakeSettingsStore()
        val repository = ProfileRepository(store, FakeProfilePictureStore())

        repository.setUsername("Ada")
        repository.setUsername("Ada Lovelace")

        assertEquals("Ada Lovelace", repository.profile.value?.username)
        assertEquals("Ada Lovelace", store.values["profile.username"])
    }

    @Test
    fun theNameIsTidiedBeforeItIsStored() {
        val store = FakeSettingsStore()

        ProfileRepository(store, FakeProfilePictureStore()).setUsername("  Ada   Lovelace ")

        assertEquals("Ada Lovelace", store.values["profile.username"])
    }

    /**
     * A half-typed name is not a name. Refusing it is what keeps the next launch
     * from finding a profile whose owner is called nothing.
     */
    @Test
    fun aNameTooShortToUseIsNotStored() {
        val store = FakeSettingsStore()
        val repository = ProfileRepository(store, FakeProfilePictureStore())

        repository.setUsername("A")

        assertNull(repository.profile.value)
        assertNull(store.values["profile.username"])
    }

    @Test
    fun anUnusableRenameLeavesTheStoredNameAlone() {
        val store = FakeSettingsStore()
        val repository = ProfileRepository(store, FakeProfilePictureStore())

        repository.setUsername("Ada")
        repository.setUsername("")

        assertEquals("Ada", repository.profile.value?.username)
    }

    /** Nothing changed is nothing to write - a field reports every keystroke. */
    @Test
    fun renamingToTheSameNameWritesNothing() {
        val store = FakeSettingsStore()
        val repository = ProfileRepository(store, FakeProfilePictureStore())

        repository.setUsername("Ada")
        val afterFirst = store.writeCount

        repository.setUsername("Ada")

        assertEquals(afterFirst, store.writeCount)
    }

    /**
     * A name written by an older build, or edited by hand into something the
     * rules no longer accept, sends the reader back through onboarding rather
     * than leaving them with a blank avatar they cannot fix.
     */
    @Test
    fun anUnusableStoredNameReadsAsNoProfile() {
        val store = FakeSettingsStore(mapOf("profile.username" to " "))

        assertNull(ProfileRepository(store, FakeProfilePictureStore()).profile.value)
    }
}
