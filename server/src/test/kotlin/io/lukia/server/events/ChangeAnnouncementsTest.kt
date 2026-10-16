package io.lukia.server.events

import io.lukia.api.BookStateRequest
import io.lukia.api.FavoritePart
import io.lukia.api.ProgressPart
import io.lukia.api.SessionRequest
import io.lukia.server.TestClock
import io.lukia.server.inMemoryDatabase
import io.lukia.server.reading.BookStateService
import io.lukia.server.reading.MobyDick
import io.lukia.server.reading.SessionService
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

/** What the reading services tell listening devices: something stored, and nothing else. */
class ChangeAnnouncementsTest {

    private val database = inMemoryDatabase()
    private val clock = TestClock()
    private val events = ChangeEvents()
    private val accountId = database.run {
        accountQueries.insert("alice", "hash", false, clock.millis())
        accountQueries.lastInsertId().executeAsOne()
    }

    @Test
    fun `a book's state is announced when stored, and not when it changes nothing`() = heard { announced ->
        val states = BookStateService(database, clock, events)
        val now = clock.millis()

        states.apply(accountId, MobyDick, BookStateRequest(progress = ProgressPart(5, 300, now)))
        // Sent again, and an older change that loses: nothing stored either time.
        states.apply(accountId, MobyDick, BookStateRequest(progress = ProgressPart(5, 300, now)))
        states.apply(accountId, MobyDick, BookStateRequest(progress = ProgressPart(2, 300, now - 1)))
        // A different part, newer: stored.
        states.apply(accountId, MobyDick, BookStateRequest(favorite = FavoritePart(true, now)))

        assertEquals(2, announced().count { it.kind == ChangeKind.BookState })
    }

    @Test
    fun `a session is announced when recorded or lengthened, and not when sent again`() = heard { announced ->
        val sessions = SessionService(database, events)
        val request = SessionRequest(bookId = MobyDick, startedAt = clock.millis(), durationMs = 60_000)

        sessions.record(accountId, SessionId, request)
        sessions.record(accountId, SessionId, request)
        sessions.record(accountId, SessionId, request.copy(durationMs = 30_000))
        sessions.record(accountId, SessionId, request.copy(durationMs = 90_000))

        assertEquals(2, announced().count { it.kind == ChangeKind.Sessions })
    }

    /** Runs [block] with a listener already subscribed, and hands it what was heard so far. */
    private fun heard(block: suspend (announced: () -> List<Change>) -> Unit) = runBlocking {
        val heard = mutableListOf<Change>()
        val listening = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            events.all.collect { heard += it }
        }
        block {
            heard.toList()
        }
        yield()
        listening.cancel()
    }

    private companion object {
        const val SessionId = "6f1c2a3e-1b2c-4d5e-8f90-123456789abc"
    }
}
