package io.lukia.features.reader.domain

/**
 * Positions in a map, for the tests that only need somewhere for a position to
 * go. What the real store does with SQL is covered against a real database in
 * `SqlReadingProgressRepositoryTest`.
 */
class FakeReadingProgressRepository(
    private val positions: MutableMap<String, ReadingProgress> = mutableMapOf(),
) : ReadingProgressRepository {

    override suspend fun progressFor(location: String): ReadingProgress? = positions[location]

    override suspend fun allProgress(): Map<String, ReadingProgress> = positions.toMap()

    override suspend fun save(location: String, progress: ReadingProgress) {
        positions[location] = progress
    }

    override suspend fun forget(location: String) {
        positions.remove(location)
    }
}
