package io.lukia.api

import kotlinx.serialization.Serializable

/*
 * One account's reading, kept per book: where they are, how they read a PDF,
 * whether it is a favourite, and the sittings they spent in it.
 *
 * Books are named by content hash (`BookResponse.id`) here too, and need not be
 * on the server - a book only on one device still has a place in it, and
 * the hours spent in it count.
 *
 * Every `changedAt` is the device's own clock, in epoch milliseconds, at the
 * moment the reader made the change - see
 * docs/decisions/0003-offline-first-sync.md. Of two changes to the same part,
 * the later one wins.
 */

/** Where the reader is. The pair is stored and replaced together, as the app's `ReadingProgress` is. */
@Serializable
data class ProgressPart(
    val pageIndex: Int,
    val pageCount: Int,
    val changedAt: Long,
)

/** How a PDF is read, by the app's own name for it (`Original`, `Text`). */
@Serializable
data class LayoutPart(
    val layout: String,
    val changedAt: Long,
)

@Serializable
data class FavoritePart(
    val isFavorite: Boolean,
    val changedAt: Long,
)

/**
 * A device's changes to one book, in parts that win or lose independently: a
 * favourite set on the phone is not undone by the desktop moving a page later.
 * A part left null is a part this change does not touch.
 */
@Serializable
data class BookStateRequest(
    val progress: ProgressPart? = null,
    val layout: LayoutPart? = null,
    val favorite: FavoritePart? = null,
)

/** Everything the account has said about one book, each part as it last won. */
@Serializable
data class BookStateResponse(
    val bookId: String,
    val progress: ProgressPart? = null,
    val layout: LayoutPart? = null,
    val favorite: FavoritePart? = null,
)

/**
 * A stretch of time spent in a book, as the app's `ReadingSession` is.
 *
 * Named by a UUID the device makes when the sitting starts, so sending it again
 * as it grows - or again after a retry - updates the one sitting rather than
 * counting it twice. Only [durationMs] ever changes, and only upwards.
 *
 * [title] is what the device called the book, kept so another device's
 * statistics can name a book it does not have.
 */
@Serializable
data class SessionRequest(
    val bookId: String,
    val startedAt: Long,
    val durationMs: Long,
    val title: String? = null,
)

@Serializable
data class SessionResponse(
    val id: String,
    val bookId: String,
    val startedAt: Long,
    val durationMs: Long,
    val title: String? = null,
)
