package io.lukia.shared.presentation.components.cover

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember

/**
 * Where a book's artwork comes from: what identifies it, and how to fetch it.
 *
 * A cover used to travel with its book as bytes on the stored entry, so the
 * shelf carried the artwork of every book on it - a hundred kilobytes each, a
 * few hundred megabytes for a large library - to draw the dozen covers the
 * reader could see.
 *
 * This is the handle that replaced them: nothing is read until a card is drawn,
 * what is read is decoded and let go of, and what is kept is the decoded artwork
 * in [CoverArtCache], under a budget that does not grow with the library.
 *
 * [key] identifies the book rather than the picture - the location the library
 * files it under - and is what the cache is keyed on and what equality is
 * decided by. That last part is what lets a card skip recomposition: [load] is a
 * lambda, and a fresh one every composition would otherwise make every cover
 * look like a different cover.
 */
@Immutable
class CoverArtSource(
    val key: String,
    private val bytes: suspend () -> ByteArray?,
) {

    /** Null for a book with no artwork stored, which most plain text is. */
    suspend fun load(): ByteArray? = bytes()

    override fun equals(other: Any?): Boolean = other is CoverArtSource && key == other.key

    override fun hashCode(): Int = key.hashCode()

    override fun toString(): String = "CoverArtSource($key)"
}

/**
 * A cover whose bytes are already to hand, which is what a screen that loaded
 * one whole book has - a detail page, a short list of what was read recently.
 *
 * Remembered rather than rebuilt, because a cover the card compares equal to is
 * a cover the card does not redraw.
 */
@Composable
fun rememberBookCover(location: String, artwork: ByteArray?): CoverArtSource? =
    remember(location, artwork) { artwork?.let { bytes -> CoverArtSource(location) { bytes } } }
