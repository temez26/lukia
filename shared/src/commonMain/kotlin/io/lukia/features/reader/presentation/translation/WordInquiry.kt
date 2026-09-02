package io.lukia.features.reader.presentation.translation

import androidx.compose.runtime.Immutable
import io.lukia.core.presentation.UiState
import io.lukia.features.translate.domain.lookup.WordEntry

/**
 * One word the reader has stopped on and asked about properly.
 *
 * The line that goes over a word in the book has to be one thing, short enough
 * to stand where the word stood - see
 * [io.lukia.features.translate.domain.text.WordLookup]. That is the right answer
 * to "what does this say" and the wrong one to "what does this mean", which is
 * the question somebody learning the language is actually asking. This is what
 * the second question opens.
 *
 * Both halves of the answer are offered rather than one of them, because they
 * are not the same kind of help: [entry] is every sense the word has, read out
 * of a dictionary and shown here, and [site] is somebody else's page about it -
 * the inflections, the etymology, the example sentences - which this app has no
 * business reproducing and every reason to link to.
 */
@Immutable
data class WordInquiry(
    val word: String,
    /** What the dictionary has to say, as it arrives. */
    val entry: UiState<WordEntry>,
    /**
     * Whether the book's own word is what is on the page underneath this.
     *
     * Carried so the panel can offer putting it back, which is what holding the
     * word used to do on its own. Nothing is lost by moving it in here - it is
     * one press either way - and what is gained is that the gesture no longer
     * has to mean two things depending on what happened last.
     */
    val showingOriginal: Boolean,
    /** Where the reader can go to read more, or null when no site is set up. */
    val site: WordSiteLink? = null,
)

/** A site and the address the word is already written into. */
@Immutable
data class WordSiteLink(val label: String, val url: String)
