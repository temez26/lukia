package io.lukia.features.reader.presentation.translation

import androidx.compose.runtime.Immutable
import io.lukia.features.reader.domain.text.TextAnchor

/**
 * Every translation in the open book, filed by where it belongs.
 *
 * Translating one sentence must not be an event for the rest of the book. Handed
 * the whole map, every page on screen looks at it again whenever any sentence
 * anywhere moves - re-splitting text, rebuilding annotated strings, re-laying
 * out paragraphs nobody touched - to arrive at "still exactly as it was".
 *
 * Split into slices that compare equal when nothing in them moved, the work
 * lands only where it is: tapping a sentence on page five leaves four and six
 * untouched, because what they are handed is the same value it was.
 */
@Immutable
class BookTranslations private constructor(
    private val pages: Map<Int, SentenceTranslations>,
    private val sections: Map<Int, SectionTranslations>,
) {

    fun page(pageIndex: Int): SentenceTranslations =
        pages[pageIndex] ?: SentenceTranslations.None

    fun section(sectionIndex: Int): SectionTranslations =
        sections[sectionIndex] ?: SectionTranslations.None

    companion object {

        val None = BookTranslations(emptyMap(), emptyMap())

        /**
         * Files the session's passages by where each of them is. One pass over
         * the whole map, done once when it changes, rather than a search of it
         * per page and per paragraph on screen.
         *
         * [notes] is where the translator's asides are dropped when the reader
         * has turned them off, rather than at each of the several places that
         * draw one - a paragraph's remarks, a panel over a page. Free here,
         * because this already walks every passage; and it is the one pass both
         * kinds of book are filed by, so neither can disagree with the other
         * about what the setting meant.
         */
        fun of(
            passages: Map<TextAnchor, PassageTranslation>,
            notes: Boolean = true,
        ): BookTranslations {
            if (passages.isEmpty()) return None

            val pages = mutableMapOf<Int, PassageBuilder>()
            val sections = mutableMapOf<Int, MutableMap<Int, PassageBuilder>>()

            passages.forEach { (anchor, passage) ->
                val state = if (notes) passage else passage.withoutNote()

                when (anchor) {
                    is TextAnchor.Page ->
                        pages
                            .getOrPut(anchor.pageIndex, ::PassageBuilder)
                            .put(anchor.sentenceIndex, anchor.wordIndex, state, anchor.blockIndex)

                    is TextAnchor.Section ->
                        sections
                            .getOrPut(anchor.sectionIndex, ::mutableMapOf)
                            .getOrPut(anchor.blockIndex, ::PassageBuilder)
                            .put(anchor.sentenceIndex, anchor.wordIndex, state)
                }
            }

            return BookTranslations(
                pages = pages.mapValues { (_, block) -> block.build() },
                sections = sections.mapValues { (_, blocks) ->
                    SectionTranslations(blocks.mapValues { (_, block) -> block.build() })
                },
            )
        }
    }
}

/**
 * The same passage with nothing under it, for a reader who has turned the
 * translator's asides off.
 *
 * Only ever a finished translation. A failure's reason is not an aside about the
 * text - a passage marked as translated with nothing to show and no reason given
 * is a reader wondering what happened - and a passage still arriving has said
 * nothing yet.
 */
private fun PassageTranslation.withoutNote(): PassageTranslation =
    if (this is PassageTranslation.Ready && translation.note != null) {
        copy(translation = translation.copy(note = null))
    } else {
        this
    }

/**
 * One block's translations while they are being gathered.
 *
 * A sentence and the individual words inside it are kept apart rather than in
 * one map keyed by both, because that is how they are read: a paragraph asks
 * "has this sentence been translated" once per sentence and "which words in it
 * have" only for the sentences that have not.
 */
private class PassageBuilder {

    private var block: PassageTranslation? = null
    private val paragraphs = mutableMapOf<Int, PassageTranslation>()
    private val sentences = mutableMapOf<Int, PassageTranslation>()
    private val words = mutableMapOf<Int, MutableMap<Int, PassageTranslation>>()

    /**
     * [blockIndex] is only ever given for a page, where one bucket holds every
     * block on it. A section's blocks are buckets of their own, so a passage
     * with no sentence named there is the bucket itself - see [TextAnchor].
     */
    fun put(
        sentenceIndex: Int?,
        wordIndex: Int?,
        state: PassageTranslation,
        blockIndex: Int? = null,
    ) {
        when {
            sentenceIndex != null && wordIndex != null ->
                words.getOrPut(sentenceIndex, ::mutableMapOf)[wordIndex] = state

            sentenceIndex != null -> sentences[sentenceIndex] = state
            blockIndex != null -> paragraphs[blockIndex] = state
            else -> block = state
        }
    }

    fun build() = SentenceTranslations(
        sentences = sentences.toMap(),
        words = words.mapValues { (_, byWord) -> WordTranslations(byWord.toMap()) },
        block = block,
        paragraphs = paragraphs.toMap(),
    )
}

/** What has become of the sentences of one section, block by block. */
@Immutable
class SectionTranslations(private val blocks: Map<Int, SentenceTranslations>) {

    fun block(blockIndex: Int): SentenceTranslations =
        blocks[blockIndex] ?: SentenceTranslations.None

    override fun equals(other: Any?): Boolean =
        other is SectionTranslations && blocks == other.blocks

    override fun hashCode(): Int = blocks.hashCode()

    companion object {
        val None = SectionTranslations(emptyMap())
    }
}

/**
 * What has become of the sentences of one block, or of one page of a
 * fixed-layout document, by sentence index - and of any single words inside
 * them the reader has asked about.
 *
 * Equality is the whole point of the type: two of these holding the same states
 * for the same sentences are the same thing to look at, which is what lets
 * everything drawing from one skip being drawn again.
 */
@Immutable
class SentenceTranslations(
    private val sentences: Map<Int, PassageTranslation>,
    private val words: Map<Int, WordTranslations> = emptyMap(),
    /**
     * What has become of the whole block, when the reader asked for the
     * paragraph rather than for anything inside it.
     *
     * It takes precedence over everything else here while it is showing: a
     * paragraph translated whole has none of its own sentences left on the page
     * to have been translated.
     */
    val block: PassageTranslation? = null,
    /**
     * What has become of whole paragraphs of a *page*, by the block each of them
     * is.
     *
     * A page is one of these for all of it, because its sentences are numbered
     * across the whole page rather than within a block - so unlike a section,
     * whose every block is a bucket of its own with a [block] slot to match,
     * a page's paragraphs have to say which one they are.
     */
    private val paragraphs: Map<Int, PassageTranslation> = emptyMap(),
) {

    /** What has become of the whole sentence, as opposed to any word in it. */
    operator fun get(sentenceIndex: Int): PassageTranslation? = sentences[sentenceIndex]

    /** The single words of that sentence somebody has asked about. */
    fun words(sentenceIndex: Int): WordTranslations =
        words[sentenceIndex] ?: WordTranslations.None

    /**
     * What has become of one paragraph of a page. It takes precedence over the
     * sentences inside it, exactly as [block] does: a paragraph translated whole
     * has none of its own sentences left on the page.
     */
    fun paragraph(blockIndex: Int): PassageTranslation? = paragraphs[blockIndex]

    val isEmpty: Boolean
        get() = block == null && paragraphs.isEmpty() && sentences.isEmpty() && words.isEmpty()

    /**
     * The same states, seen from a slice of the block that starts [offset]
     * sentences into it.
     *
     * A paragraph too long for one page is drawn in pieces, and each piece
     * splits its own text into sentences numbered from zero - while the book,
     * and everything translated in it, counts them from the start of the
     * paragraph. This is the one place the two numberings meet.
     */
    fun from(offset: Int): SentenceTranslations {
        if (offset == 0 || isEmpty) return this

        return SentenceTranslations(
            sentences = sentences.shiftedBy(offset),
            words = words.shiftedBy(offset),
            block = block,
            paragraphs = paragraphs,
        )
    }

    override fun equals(other: Any?): Boolean =
        other is SentenceTranslations &&
            block == other.block &&
            paragraphs == other.paragraphs &&
            sentences == other.sentences &&
            words == other.words

    override fun hashCode(): Int =
        31 * (31 * (31 * sentences.hashCode() + words.hashCode()) + block.hashCode()) +
            paragraphs.hashCode()

    companion object {
        val None = SentenceTranslations(emptyMap())
    }
}

private fun <T> Map<Int, T>.shiftedBy(offset: Int): Map<Int, T> =
    mapNotNull { (index, value) -> (index - offset).takeIf { it >= 0 }?.let { it to value } }
        .toMap()

/**
 * What has become of single words inside one sentence.
 *
 * Separate from the sentence's own state rather than folded in with it, because
 * the two do not overlap: a sentence translated whole has no words of its own
 * left on screen to mark, and a sentence still in the book's language is exactly
 * where a translated word can be.
 */
@Immutable
class WordTranslations(private val byWord: Map<Int, PassageTranslation>) {

    operator fun get(wordIndex: Int): PassageTranslation? = byWord[wordIndex]

    val isEmpty: Boolean get() = byWord.isEmpty()

    override fun equals(other: Any?): Boolean =
        other is WordTranslations && byWord == other.byWord

    override fun hashCode(): Int = byWord.hashCode()

    companion object {
        val None = WordTranslations(emptyMap())
    }
}
