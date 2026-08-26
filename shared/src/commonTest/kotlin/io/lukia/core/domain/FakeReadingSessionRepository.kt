package io.lukia.core.domain

/**
 * Sittings in a list, for the tests that only need somewhere for one to go.
 *
 * Rows are addressed by their position, exactly as the real store addresses them
 * by rowid, so a recorder that extends the wrong sitting fails here too.
 */
class FakeReadingSessionRepository(
    initial: List<ReadingSession> = emptyList(),
) : ReadingSessionRepository {

    private val rows = initial.toMutableList()

    /**
     * How many times a sitting's length has been written down.
     *
     * Counted because how often the recorder writes is part of what it is for:
     * it stores as it goes so that a process being killed costs seconds rather
     * than an evening, and it must stop once there is nothing left to store.
     */
    var lengthsWritten = 0
        private set

    override suspend fun start(location: String, startedAt: Long): Long {
        rows.add(ReadingSession(location = location, startedAt = startedAt, durationMs = 0))

        return rows.lastIndex.toLong()
    }

    override suspend fun extend(id: Long, durationMs: Long) {
        val index = id.toInt()

        lengthsWritten++

        rows[index] = rows[index].copy(durationMs = durationMs)
    }

    override suspend fun all(): List<ReadingSession> = rows.toList()

    override suspend fun forget(location: String) {
        rows.removeAll { it.location == location }
    }
}
