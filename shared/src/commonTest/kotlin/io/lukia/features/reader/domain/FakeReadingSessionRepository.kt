package io.lukia.features.reader.domain

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

    override suspend fun start(location: String, startedAt: Long): Long {
        rows.add(ReadingSession(location = location, startedAt = startedAt, durationMs = 0))

        return rows.lastIndex.toLong()
    }

    override suspend fun extend(id: Long, durationMs: Long) {
        val index = id.toInt()

        rows[index] = rows[index].copy(durationMs = durationMs)
    }

    override suspend fun all(): List<ReadingSession> = rows.toList()

    override suspend fun forget(location: String) {
        rows.removeAll { it.location == location }
    }
}
