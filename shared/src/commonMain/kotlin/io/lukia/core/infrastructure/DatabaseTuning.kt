package io.lukia.core.infrastructure

/**
 * How long a statement waits for a database somebody else is writing to, before
 * giving up on it.
 *
 * One database, many threads: every repository moves its work to the IO
 * dispatcher, so the shelf being read, a sitting being extended and a
 * translation being cached genuinely do land at once. With no timeout the second
 * fails immediately with `SQLITE_BUSY`, reaching the reader as a book that would
 * not open for no reason they can see or repeat.
 *
 * Five seconds is far longer than any write here takes - the largest is one
 * cover - so a wait this long means something is wrong rather than busy, and
 * failing is the honest answer.
 */
internal const val BusyTimeoutMillis = 5_000

/**
 * Whether this is SQLite saying the file is not a database it can read.
 *
 * A database that cannot be opened is put aside and started again empty, costing
 * the reader their library index - so the test has to be narrow. A migration
 * that throws, a full disk, a directory the app may not write to: none is the
 * file being broken, and answering "corrupt" to any would throw away a good
 * library over something that would have fixed itself.
 *
 * Matched on SQLite's own words rather than a platform exception type: three
 * platforms mean three unrelated hierarchies over one C library that has phrased
 * this three ways for twenty years. The wording is part of what SQLite
 * guarantees in a way that `SQLiteException` on the JVM and a
 * `NullPointerException` out of a native driver are not.
 */
internal fun Throwable.isDatabaseCorruption(): Boolean =
    generateSequence(this, Throwable::cause)
        .take(CauseDepth)
        .any { failure -> failure.message.orEmpty().lowercase().namesACorruptDatabase() }

private fun String.namesACorruptDatabase(): Boolean = CorruptionPhrases.any { it in this }

/**
 * `SQLITE_NOTADB` for a file whose header is not a database's, and
 * `SQLITE_CORRUPT` for one whose header is but whose pages are not.
 *
 * The first is a fragment rather than a whole message on purpose: SQLite phrases
 * that one at least three ways depending on which binding is asking - "file is
 * not a database", "file is encrypted or is not a database", "File opened that
 * is not a database file" - and all three say the same thing around these three
 * words. Nothing SQLite says about a disk being full, a directory it may not
 * write to, or a statement this app got wrong contains them.
 */
private val CorruptionPhrases = listOf(
    "not a database",
    "database disk image is malformed",
)

/** A wrapped failure is a driver's doing, not a chain of arbitrary length. */
private const val CauseDepth = 8
