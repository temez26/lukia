package io.lukia.core.domain.diagnostics

/** How much a maintainer reading a report backwards cares about the entry. */
enum class DiagnosticLevel {
    /** A breadcrumb. On its own it says nothing; it is what gives the rest context. */
    Info,

    /** Something degraded and the app carried on with less than it wanted. */
    Warning,

    /** The reader watched something not work. */
    Failure,
}

/**
 * Where in the app the entry was made.
 *
 * A closed set rather than a free string. A tag somebody types is "pdf" in one
 * file and "PdfTextReader" in the next, and then neither a filter over these
 * nor a grep over an exported report finds both. Adding a fifth is a decision
 * about what this app is made of, not a convenience at a call site.
 */
enum class DiagnosticArea { Import, Reading, Translation, Storage }

/**
 * Somewhere to write down what the app just decided not to tell anybody.
 *
 * The app absorbs failures on purpose in a dozen places - a cover that will not
 * decode, a page that will not parse, a file whose encoding was guessed at -
 * because none of them is worth failing an import or closing a book over. What
 * the reader gets is a grey placeholder, a blank page, mojibake; what a
 * maintainer gets, without this, is a bug report describing one of those and
 * nothing else at all.
 *
 * Not suspending, and it takes no dispatcher. Every call site is inside a parser
 * or a decoder that is not a coroutine and has no business becoming one in order
 * to say that a cover failed to decode - a dispatcher is injected for work, and
 * an entry is a record rather than work. Implementations must keep [record] to
 * an in-memory append for the same reason.
 */
interface Diagnostics {

    fun record(
        level: DiagnosticLevel,
        area: DiagnosticArea,
        /**
         * A constant, never a sentence: `"cover-extraction-failed"`, not
         * `"could not extract a cover from $title"`.
         *
         * The constant is what a maintainer greps for, and it is the half of an
         * entry that cannot leak anything, because nothing about the reader can
         * reach it.
         */
        event: String,
        /**
         * The only interpolated part, and so the only part that has to be
         * written with suspicion. Counts, formats, extensions, statuses,
         * durations.
         *
         * Never a title, an author, a path, a file name, a passage, a key or an
         * address the reader typed. A library is a political and a medical
         * record, and a desktop path carries the reader's own name.
         * `DiagnosticsRedactionTest` is what holds this to be true.
         */
        detail: String? = null,
        /**
         * Kept for its type and never for its message - see
         * [io.lukia.core.domain.diagnostics.DiagnosticEntry.causeType].
         */
        cause: Throwable? = null,
    )

    companion object {
        /**
         * Records nothing.
         *
         * The default wherever a boundary is reached by code that has no reason
         * to carry a recorder - a PDF's own string decoder, and every test that
         * is not about diagnostics.
         */
        val None: Diagnostics = object : Diagnostics {
            override fun record(
                level: DiagnosticLevel,
                area: DiagnosticArea,
                event: String,
                detail: String?,
                cause: Throwable?,
            ) = Unit
        }
    }
}

fun Diagnostics.info(
    area: DiagnosticArea,
    event: String,
    detail: String? = null,
) = record(DiagnosticLevel.Info, area, event, detail)

fun Diagnostics.warn(
    area: DiagnosticArea,
    event: String,
    detail: String? = null,
    cause: Throwable? = null,
) = record(DiagnosticLevel.Warning, area, event, detail, cause)

fun Diagnostics.failure(
    area: DiagnosticArea,
    event: String,
    detail: String? = null,
    cause: Throwable? = null,
) = record(DiagnosticLevel.Failure, area, event, detail, cause)
