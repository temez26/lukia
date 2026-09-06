package io.lukia.features.reader.presentation.screens.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import io.lukia.core.domain.text.PassageUnit
import io.lukia.features.reader.domain.text.Sentence
import io.lukia.features.reader.domain.text.TextAnchor
import io.lukia.features.reader.domain.text.TextSection
import io.lukia.features.reader.domain.text.Word
import io.lukia.features.reader.presentation.components.passages.BlockActions
import io.lukia.features.reader.presentation.components.passages.NoBlockActions
import io.lukia.features.reader.presentation.components.passages.PassageActions
import io.lukia.features.reader.presentation.components.passages.WordGesture
import io.lukia.features.reader.presentation.pagination.PageBlock
import io.lukia.features.reader.presentation.pagination.TextPage
import io.lukia.features.reader.presentation.translation.PassageSelection
import io.lukia.features.translate.domain.MAX_TRANSLATABLE_CHARS

/**
 * What a tap, a long press and a call for the whole page mean on one page.
 *
 * Remembered per page rather than rebuilt every composition, because a fresh set
 * of callbacks for every block on every recomposition would make every one of
 * those blocks recompose too - which is the exact cost the sliced translations
 * exist to avoid.
 *
 * The callbacks are read through [rememberUpdatedState] rather than captured, so
 * remembering these does not pin the reader of a moment ago.
 */
@Composable
internal fun rememberPageActions(
    page: TextPage,
    sections: List<TextSection>,
    onToggleChrome: () -> Unit,
    onPassageSelected: (PassageSelection) -> Unit,
    onPassageHold: (PassageSelection) -> Unit,
    onPassageInquiry: (PassageSelection) -> Unit,
    onPassagesRequested: (List<PassageSelection>) -> Unit,
): (PageBlock) -> BlockActions {
    val answering = PageCallbacks(
        toggleChrome = rememberUpdatedState(onToggleChrome),
        selected = rememberUpdatedState(onPassageSelected),
        held = rememberUpdatedState(onPassageHold),
        inquiry = rememberUpdatedState(onPassageInquiry),
        requested = rememberUpdatedState(onPassagesRequested),
    )

    return remember(page, sections) {
        val byBlock = page.blocks.associateWith { pageBlock ->
            pageBlock.passageActions(
                chapterTitle = sections.getOrNull(pageBlock.sectionIndex)?.title,
                answering = answering,
            )
        }

        fun(pageBlock: PageBlock): BlockActions = byBlock[pageBlock] ?: NoBlockActions
    }
}

/**
 * Everywhere a page can send what the reader pointed at.
 *
 * One value rather than five arguments because they always travel together and
 * always come from the same place. Each is read through [rememberUpdatedState],
 * so remembering these does not pin the reader of a moment ago.
 */
private class PageCallbacks(
    val toggleChrome: State<() -> Unit>,
    val selected: State<(PassageSelection) -> Unit>,
    val held: State<(PassageSelection) -> Unit>,
    val inquiry: State<(PassageSelection) -> Unit>,
    val requested: State<(List<PassageSelection>) -> Unit>,
)

private fun PageBlock.passageActions(
    chapterTitle: String?,
    answering: PageCallbacks,
): BlockActions {
    fun sentenceOf(sentence: Sentence, paragraph: String) = selection(
        chapterTitle = chapterTitle,
        sentenceIndex = sentence.index,
        passage = sentence.text,
        paragraph = paragraph,
        unit = PassageUnit.Sentence,
    )

    return PassageActions(
        onSentence = { _, sentence, paragraph -> answering.selected.value(sentenceOf(sentence, paragraph)) },

        // A word travels with the paragraph around it, exactly as a sentence
        // does. On its own it is a dictionary entry; what makes it a reading is
        // the clause it was taken out of.
        //
        // A held word goes to whoever can tell whether it has been answered
        // already, which is what decides between asking about it and opening
        // what else it means - see ReaderViewModel.onPassageHold. A questioned
        // one is asking what it means whatever has become of it.
        onWord = { _, sentence, word, paragraph, gesture ->
            val selection = selection(
                chapterTitle = chapterTitle,
                sentenceIndex = sentence.index,
                passage = word.text,
                paragraph = paragraph,
                unit = PassageUnit.Word,
                wordIndex = word.index,
            )

            when (gesture) {
                WordGesture.Tapped -> answering.selected.value(selection)
                WordGesture.Held -> answering.held.value(selection)
                WordGesture.Questioned -> answering.inquiry.value(selection)
            }
        },

        // A paragraph is its own context, so it travels without one: there is
        // nothing around it left to send that is not already in it.
        onParagraph = { _, paragraph ->
            answering.selected.value(
                selection(
                    chapterTitle = chapterTitle,
                    sentenceIndex = null,
                    passage = paragraph,
                    paragraph = paragraph,
                    unit = PassageUnit.Paragraph,
                ),
            )
        },

        // A screenful, asked for as the paragraphs it is made of rather than as
        // every sentence in them - see bulkPassages.
        onBlock = { _, sentences, paragraph ->
            answering.requested.value(bulkPassages(chapterTitle, sentences, paragraph))
        },

        onElsewhere = { answering.toggleChrome.value() },
    ).forBlock(blockIndex)
}

/**
 * One block of the book, as the passages a standing order asks about.
 *
 * The paragraph whole, in one request, rather than a request per sentence in it.
 * Measured against a model running on the reader's own machine, on a block of
 * twelve sentences:
 *
 *     a request per sentence, four at once   44s, 7,500 tokens
 *     the paragraph, in one request          12s, 1,000 tokens
 *
 * Nearly all of that difference is the same work done over and over. Every one
 * of those twelve requests carried the standing instructions, the book, and the
 * whole paragraph as context - so the model read the paragraph twelve times to
 * translate it once, and thought about it twelve times over.
 *
 * Two blocks still go as sentences. One too long to send in a single request,
 * which is a document with no sentence punctuation to speak of rather than a
 * long paragraph - see [MAX_TRANSLATABLE_CHARS]. And a paragraph the layout cut
 * across two pages, because a piece of a paragraph is not one: the answer is
 * filed under the block, and the piece on the other page would show the same
 * translation over its own different words. Sentences are anchored finely enough
 * to survive being split, which is why they were what this sent before.
 */
private fun PageBlock.bulkPassages(
    chapterTitle: String?,
    sentences: List<Sentence>,
    paragraph: String,
): List<PassageSelection> {
    if (!whole || paragraph.length > MAX_TRANSLATABLE_CHARS) {
        return sentences.map { sentence ->
            selection(
                chapterTitle = chapterTitle,
                sentenceIndex = sentence.index,
                passage = sentence.text,
                paragraph = paragraph,
                unit = PassageUnit.Sentence,
            )
        }
    }

    return listOf(
        selection(
            chapterTitle = chapterTitle,
            sentenceIndex = null,
            passage = paragraph,
            paragraph = paragraph,
            unit = PassageUnit.Paragraph,
        ),
    )
}

/**
 * A passage somebody pointed at, addressed by where it lives in the book rather
 * than by where it is on the page.
 *
 * [PageBlock.sentenceOffset] is added back on here: a paragraph split across two
 * pages numbers its sentences from zero on each of them, while the book counts
 * them from the start of the paragraph.
 */
private fun PageBlock.selection(
    chapterTitle: String?,
    sentenceIndex: Int?,
    passage: String,
    paragraph: String,
    unit: PassageUnit,
    wordIndex: Int? = null,
) = PassageSelection(
    anchor = TextAnchor.Section(
        sectionIndex = sectionIndex,
        blockIndex = blockIndex,
        sentenceIndex = sentenceIndex?.plus(sentenceOffset),
        wordIndex = wordIndex,
    ),
    passage = passage,
    chapterTitle = chapterTitle,
    surroundingText = paragraph,
    unit = unit,
)
