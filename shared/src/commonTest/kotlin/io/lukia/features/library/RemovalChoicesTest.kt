package io.lukia.features.library

import io.lukia.features.library.presentation.model.RemoveFrom
import io.lukia.features.library.presentation.model.removalChoices
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Where a selection can be removed from is where its books are, and the server only for its admin. */
class RemovalChoicesTest {

    private val deviceOnly = book("Notes").document
    private val both = book("Solaris", onServer = true).document
    private val serverOnly = book("Dune", onServer = true, exists = false).document

    @Test
    fun booksOnlyHereAreOnlyAskedAboutThisDevice() {
        assertEquals(listOf(RemoveFrom.Device), listOf(deviceOnly).removalChoices(canRemoveFromServer = true))
    }

    @Test
    fun theAdminIsOfferedTheServerAndBothForBooksInBothPlaces() {
        assertEquals(
            listOf(RemoveFrom.Device, RemoveFrom.Server, RemoveFrom.DeviceAndServer),
            listOf(both).removalChoices(canRemoveFromServer = true),
        )
    }

    @Test
    fun everyoneElseIsOfferedOnlyThisDevice() {
        assertEquals(listOf(RemoveFrom.Device), listOf(both).removalChoices(canRemoveFromServer = false))
        assertTrue(listOf(serverOnly).removalChoices(canRemoveFromServer = false).isEmpty())
    }

    @Test
    fun aBookOnlyOnTheServerHasNothingHereToRemove() {
        assertEquals(listOf(RemoveFrom.Server), listOf(serverOnly).removalChoices(canRemoveFromServer = true))
        assertFalse(RemoveFrom.Device.reaches(serverOnly))
        assertTrue(RemoveFrom.DeviceAndServer.reaches(serverOnly))
    }
}
