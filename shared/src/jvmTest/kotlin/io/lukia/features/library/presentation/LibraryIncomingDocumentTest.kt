package io.lukia.features.library.presentation

import androidx.lifecycle.viewModelScope
import io.lukia.core.domain.FakeIncomingDocumentStore
import io.lukia.core.domain.IncomingDocument
import io.lukia.core.infrastructure.DocumentInbox
import io.lukia.features.library.TypeFromExtensionAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A book handed to Lukia by another app - shared out of a mail message, opened
 * from Files, tapped in a browser's downloads.
 *
 * What is pinned here is the ordering the whole feature rests on: the document
 * is copied into storage the app owns *before* anything about it is written to
 * the shelf. What arrives is readable only while the grant that delivered it
 * lasts - on Android an `ACTION_VIEW` grant cannot be made persistable at all -
 * so a library entry addressed the way the sender addressed it would open once
 * and then fail for good, with nothing on the shelf to say why.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryIncomingDocumentTest {

    private val inbox = DocumentInbox()
    private val store = FakeIncomingDocumentStore()

    private fun shared(fileName: String = "shared.pdf") = IncomingDocument(
        fileName = fileName,
        read = { "a book".encodeToByteArray() },
    )

    /**
     * A test with a library that is listening to [inbox], and a say in when that
     * library comes into being - which one of these needs to delay.
     *
     * Not a [io.lukia.MainDispatcherTest], unlike its neighbours. That installs
     * a main dispatcher carrying a scheduler of its own, which is enough while a
     * test only calls into the ViewModel: the call runs eagerly and has finished
     * by the time it returns. The inbox is collected rather than called, and a
     * document already waiting in it when collection starts is handed over
     * through a suspension that the test's own scheduler has to run - so the two
     * have to share one, or `aBookDeliveredBeforeTheLibraryExistsIsNotLost`
     * passes for the wrong reason.
     *
     * The library's scope is cancelled before the dispatcher goes back, because
     * it is not a child of the test's: left running, its collector outlives the
     * test and the next thing it touches finds no main dispatcher at all.
     */
    private fun receiving(block: suspend TestScope.(open: () -> LibraryViewModel) -> Unit) =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))

            var library: LibraryViewModel? = null

            try {
                block {
                    createViewModel(
                        repository = FakeLibraryRepository(),
                        analyzer = TypeFromExtensionAnalyzer(),
                        incomingDocuments = inbox,
                        incomingDocumentStore = store,
                    ).also { library = it }
                }
            } finally {
                library?.viewModelScope?.cancel()
                Dispatchers.resetMain()
            }
        }

    private fun LibraryViewModel.locations() = documents().map { it.document.location }

    @Test
    fun aSharedBookIsKeptAndThenImported() = receiving { open ->
        val library = open()

        inbox.deliver(shared())
        runCurrent()

        assertEquals(listOf("shared.pdf"), store.kept)
        assertEquals(listOf("/incoming/shared.pdf"), library.locations())
    }

    @Test
    fun theShelfStoresTheKeptCopyRatherThanWhereItCameFrom() = receiving { open ->
        val library = open()

        inbox.deliver(shared())
        runCurrent()

        // The point of the path: nothing the sender addressed the document by
        // may reach the library, because none of it resolves tomorrow.
        assertTrue(
            library.locations().none { it.startsWith("content://") },
            "a delivered location reached the shelf: ${library.locations()}",
        )
    }

    @Test
    fun aDocumentThatCannotBeReadIsReportedRatherThanThrown() = receiving { open ->
        store.failing = true
        val library = open()

        inbox.deliver(shared())
        runCurrent()

        assertNotNull(library.message.value, "expected a notice about the failed import")
        assertEquals(emptyList(), library.locations())
    }

    @Test
    fun theInboxKeepsWorkingAfterOneDocumentFails() = receiving { open ->
        store.failing = true
        val library = open()

        inbox.deliver(shared("broken.pdf"))
        runCurrent()

        store.failing = false
        inbox.deliver(shared("good.pdf"))
        runCurrent()

        // A throw escaping the collector would cancel it and silently stop every
        // document delivered for the rest of the session.
        assertEquals(listOf("good.pdf"), store.kept)
        assertEquals(listOf("/incoming/good.pdf"), library.locations())
    }

    @Test
    fun aBookDeliveredBeforeTheLibraryExistsIsNotLost() = receiving { open ->
        // A cold start: the intent is handled in `onCreate`, long before there
        // is a composition to hold a ViewModel.
        inbox.deliver(shared())

        val library = open()
        runCurrent()

        assertEquals(listOf("shared.pdf"), store.kept)
        assertEquals(listOf("/incoming/shared.pdf"), library.locations())
    }

    @Test
    fun twoBooksSharedAtOnceBothArrive() = receiving { open ->
        val library = open()

        inbox.deliver(shared("first.pdf"))
        inbox.deliver(shared("second.pdf"))
        runCurrent()

        // In the order they were sent: the inbox is a queue, and the session
        // finishes one book before starting the next.
        assertEquals(listOf("first.pdf", "second.pdf"), store.kept)
        assertEquals(
            setOf("/incoming/first.pdf", "/incoming/second.pdf"),
            library.locations().toSet(),
        )
    }
}
