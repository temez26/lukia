package io.lukia.features.reader.domain

/**
 * A stretch of time somebody spent in one book.
 *
 * [startedAt] is a wall clock in milliseconds, which is what makes a sitting
 * belong to a day - see `ReadingSession.sq` for why this is the one thing in
 * this app's storage that trusts a device clock.
 *
 * A sitting is written while it is happening and its [durationMs] grows as it
 * goes on, so one being read here may be one somebody is still in.
 */
data class ReadingSession(
    val location: String,
    val startedAt: Long,
    val durationMs: Long,
)

/**
 * How long each book has been read for, and when.
 *
 * Apart from [ReadingProgressRepository] because the two answer different
 * questions about the same act: one is where the reader is in a book, which is
 * replaced every time they move, and this is what they did with their evening,
 * which is only ever added to.
 */
interface ReadingSessionRepository {

    /**
     * Opens a sitting in the book at [location] and hands back its id, which is
     * what [extend] writes against.
     *
     * Opened at the start rather than written at the end so that a sitting cut
     * short by the app being killed is still most of a sitting.
     */
    suspend fun start(location: String, startedAt: Long): Long

    /** Rewrites how long the sitting with [id] has run. */
    suspend fun extend(id: Long, durationMs: Long)

    /** Every sitting there has been, oldest first. */
    suspend fun all(): List<ReadingSession>

    /**
     * Forgets everything read in the book at [location].
     *
     * Not called when a book leaves the library - the reading happened, and the
     * statistics are about the reader rather than about the shelf. It is here
     * for the reader who wants a book's history gone as well as the book.
     */
    suspend fun forget(location: String)
}
