package io.lukia.features.reader.presentation.translation

import androidx.compose.runtime.Immutable
import io.lukia.core.domain.AppError
import io.lukia.core.presentation.UiState
import io.lukia.features.translate.domain.lookup.WordEntry
import io.lukia.features.translate.domain.lookup.WordSite

/**
 * One word the reader has stopped on and asked about properly.
 *
 * The line that goes over a word in the book must be short enough to stand where
 * the word stood - see [io.lukia.features.reader.domain.text.WordLookup]. That
 * answers "what does this say" and not "what does this mean", which is what
 * somebody learning the language is asking. This is what that second question
 * opens.
 *
 * Both halves are offered because they are different kinds of help: [entry] is
 * every sense the word has, read out of a dictionary and shown here, and [site]
 * is somebody else's page about it - inflections, etymology, examples - which
 * this app has no business reproducing and every reason to link to.
 */
@Immutable
data class WordInquiry(
    val word: String,
    /** What the dictionary has to say, as it arrives. */
    val entry: UiState<WordEntry>,
    /** What the word is doing on the page underneath this. */
    val onThePage: WordOnThePage,
    /** Where the reader can go to read more, or null when no site is set up. */
    val site: WordSiteLink? = null,
)

/**
 * What has become of the word on the page, which is what the panel can offer to
 * do about it.
 *
 * The three are what holding a word used to do on its own, gathered into one
 * place because the hold now opens this panel instead: a translated word could
 * be put back, a word put back could be brought forward again, and one that
 * failed could be asked about again. Nothing is lost by moving them in here -
 * each is one press either way - and what is gained is that the gesture no
 * longer means three things depending on what happened last.
 */
@Immutable
sealed interface WordOnThePage {

    /**
     * Nothing has happened to it: the book's own word is there because nobody
     * has asked about it yet.
     *
     * Reached only by the gesture that asks what a word means outright - the
     * second mouse button, two fingers on a trackpad - which is a question about
     * the word rather than a request to translate the page. A hold cannot arrive
     * here: its first job is translating, and it opens this panel only once that
     * has happened.
     */
    data object Untouched : WordOnThePage

    /** The translation is standing where the word was. */
    data object Translated : WordOnThePage

    /** The reader has put the book's own word back. */
    data object Original : WordOnThePage

    /**
     * Looking it up failed, and [error] says why.
     *
     * The reason a failure opens this panel at all rather than quietly asking
     * again. A word the reader's own service could not answer is exactly the
     * word they want to take somewhere else, and asking the same service a
     * second time is the one thing least likely to help - so the dictionary is
     * read, the site is offered, and asking again is one of the things on the
     * panel rather than the only thing the gesture can do.
     */
    data class Failed(val error: AppError) : WordOnThePage
}

/**
 * A site and the address the word is already written into.
 *
 * The site rather than its name: this is built where the reader's settings are
 * read, which is not a composition, and what a site is called is a string
 * resource - see `TranslationChoicesDisplay.kt`.
 */
@Immutable
data class WordSiteLink(val site: WordSite, val url: String)
