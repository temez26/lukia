package io.lukia.core.infrastructure

/**
 * How long a statement waits for a database somebody else is writing to, before
 * giving up on it.
 *
 * There is one database and there are many threads: every repository moves its
 * work to the IO dispatcher, so the shelf being read, a sitting being extended
 * and a translation being cached are three pieces of work that genuinely do
 * land at once. With no timeout the second of them fails immediately with
 * `SQLITE_BUSY`, which reaches the reader as a book that would not open for no
 * reason they can see or repeat.
 *
 * Five seconds is far longer than any write here takes - the largest is one
 * cover - so a wait this long means something is wrong rather than busy, and
 * failing is then the honest answer.
 */
internal const val BusyTimeoutMillis = 5_000

/**
 * Whether this is SQLite saying the file is not a database it can read.
 *
 * The difference matters more than it looks. A database that cannot be opened
 * is put aside and started again empty, which costs the reader their library
 * index - so the test for it has to be narrow. A migration that throws, a disk
 * that is full, a directory the app may not write to: none of those is the file
 * being broken, and answering "corrupt" to any of them would throw away a
 * perfectly good library over something that would have fixed itself.
 *
 * Matched on SQLite's own words rather than on a platform's exception type,
 * because there are three platforms here and three unrelated exception
 * hierarchies over one C library that phrases this three ways and has done for
 * twenty years. The wording is part of what SQLite guarantees in a way that
 * `SQLiteException` on the JVM and a `NullPointerException` out of a native
 * driver are not.
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
