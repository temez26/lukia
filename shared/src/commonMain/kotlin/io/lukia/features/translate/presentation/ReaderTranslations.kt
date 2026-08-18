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
    private val sections: Map<Int, SectionTranslations>
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

            val pages = mutableMapOf<Int, MutableMap<Int, PassageTranslation>>()
            val sections = mutableMapOf<Int, MutableMap<Int, MutableMap<Int, PassageTranslation>>>()

            passages.forEach { (anchor, state) ->
                when (anchor) {
                    is TextAnchor.Page ->
                        pages.getOrPut(anchor.pageIndex, ::mutableMapOf)[anchor.sentenceIndex] = state

                    is TextAnchor.Section ->
                        sections.getOrPut(anchor.sectionIndex, ::mutableMapOf)
                            .getOrPut(anchor.blockIndex, ::mutableMapOf)[anchor.sentenceIndex] = state
                }
            }

            return ReaderTranslations(
                pages = pages.mapValues { (_, sentences) -> SentenceTranslations(sentences) },
                sections = sections.mapValues { (_, blocks) ->
                    SectionTranslations(
                        blocks.mapValues { (_, sentences) -> SentenceTranslations(sentences) }
                    )
                }
            )
        }
    }
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
 * fixed-layout document, by sentence index.
 *
 * Equality is the whole point of the type: two of these holding the same states
 * for the same sentences are the same thing to look at, which is what lets
 * everything drawing from one skip being drawn again.
 */
@Immutable
class SentenceTranslations(private val bySentence: Map<Int, PassageTranslation>) {

    operator fun get(sentenceIndex: Int): PassageTranslation? = bySentence[sentenceIndex]

    val isEmpty: Boolean get() = bySentence.isEmpty()

    override fun equals(other: Any?): Boolean =
        other is SentenceTranslations && bySentence == other.bySentence

    override fun hashCode(): Int = bySentence.hashCode()

    companion object {
        val None = SentenceTranslations(emptyMap())
    }
}
