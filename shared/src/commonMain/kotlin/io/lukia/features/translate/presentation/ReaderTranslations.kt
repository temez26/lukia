package io.lukia.features.translate.presentation

import androidx.compose.runtime.Immutable
import io.lukia.features.translate.domain.TextAnchor

/**
 * Every translation in the open book, filed by where it belongs.
 *
 * The reason this exists is that translating one sentence must not be an event
 * for the rest of the book. Handed the whole map, every page and every paragraph
 * on screen has to look at it again each time any sentence anywhere changes
 * state - re-splitting text, rebuilding annotated strings, re-laying out
 * paragraphs nobody touched - which is a lot of work to arrive at "still
 * exactly as it was".
 *
 * Split into slices that compare equal when nothing in them moved, the work
 * lands only where it is: tapping a sentence on page five leaves pages four and
 * six untouched, because what they are handed is the same value it was.
 */
@Immutable
class ReaderTranslations private constructor(
    private val pages: Map<Int, SentenceTranslations>,
    private val sections: Map<Int, SectionTranslations>,
) {

    fun page(pageIndex: Int): SentenceTranslations =
        pages[pageIndex] ?: SentenceTranslations.None

    fun section(sectionIndex: Int): SectionTranslations =
        sections[sectionIndex] ?: SectionTranslations.None

    companion object {

        val None = ReaderTranslations(emptyMap(), emptyMap())

        /**
         * Files the session's passages by where each of them is. One pass over
         * the whole map, done once when it changes, rather than a search of it
         * per page and per paragraph on screen.
         */
        fun of(passages: Map<TextAnchor, PassageTranslation>): ReaderTranslations {
            if (passages.isEmpty()) return None

            val pages = mutableMapOf<Int, PassageBuilder>()
            val sections = mutableMapOf<Int, MutableMap<Int, PassageBuilder>>()

            passages.forEach { (anchor, state) ->
                val builder = when (anchor) {
                    is TextAnchor.Page ->
                        pages.getOrPut(anchor.pageIndex, ::PassageBuilder)

                    is TextAnchor.Section ->
                        sections.getOrPut(anchor.sectionIndex, ::mutableMapOf)
                            .getOrPut(anchor.blockIndex, ::PassageBuilder)
                }

                builder.put(anchor.sentenceIndex, anchor.wordIndex, state)
            }

            return ReaderTranslations(
                pages = pages.mapValues { (_, block) -> block.build() },
                sections = sections.mapValues { (_, blocks) ->
                    SectionTranslations(blocks.mapValues { (_, block) -> block.build() })
                },
            )
        }
    }
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
    private val sentences = mutableMapOf<Int, PassageTranslation>()
    private val words = mutableMapOf<Int, MutableMap<Int, PassageTranslation>>()

    fun put(sentenceIndex: Int?, wordIndex: Int?, state: PassageTranslation) {
        when {
            // No sentence named is the block itself - see TextAnchor.
            sentenceIndex == null -> block = state
            wordIndex == null -> sentences[sentenceIndex] = state
            else -> words.getOrPut(sentenceIndex, ::mutableMapOf)[wordIndex] = state
        }
    }

    fun build() = SentenceTranslations(
        sentences = sentences.toMap(),
        words = words.mapValues { (_, byWord) -> WordTranslations(byWord.toMap()) },
        block = block,
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
) {

    /** What has become of the whole sentence, as opposed to any word in it. */
    operator fun get(sentenceIndex: Int): PassageTranslation? = sentences[sentenceIndex]

    /** The single words of that sentence somebody has asked about. */
    fun words(sentenceIndex: Int): WordTranslations =
        words[sentenceIndex] ?: WordTranslations.None

    val isEmpty: Boolean get() = block == null && sentences.isEmpty() && words.isEmpty()

    override fun equals(other: Any?): Boolean =
        other is SentenceTranslations &&
            block == other.block &&
            sentences == other.sentences &&
            words == other.words

    override fun hashCode(): Int =
        31 * (31 * sentences.hashCode() + words.hashCode()) + block.hashCode()

    companion object {
        val None = SentenceTranslations(emptyMap())
    }
}

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
