package io.lukia.features.reader.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import io.lukia.features.reader.domain.TextSection
import io.lukia.features.reader.presentation.components.BlockActions
import io.lukia.features.reader.presentation.components.NoBlockActions
import io.lukia.features.reader.presentation.components.PassageActions
import io.lukia.features.reader.presentation.pagination.PageBlock
import io.lukia.features.reader.presentation.pagination.TextPage
import io.lukia.features.translate.domain.PassageUnit
import io.lukia.features.translate.domain.Sentence
import io.lukia.features.translate.domain.TextAnchor
import io.lukia.features.translate.domain.Word
import io.lukia.features.translate.presentation.PassageSelection

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
    onPassagesRequested: (List<PassageSelection>) -> Unit,
): (PageBlock) -> BlockActions {
    val toggleChrome = rememberUpdatedState(onToggleChrome)
    val selected = rememberUpdatedState(onPassageSelected)
    val requested = rememberUpdatedState(onPassagesRequested)

    return remember(page, sections) {
        val byBlock = page.blocks.associateWith { pageBlock ->
            pageBlock.passageActions(
                chapterTitle = sections.getOrNull(pageBlock.sectionIndex)?.title,
                toggleChrome = toggleChrome,
                selected = selected,
                requested = requested,
            )
        }

        fun(pageBlock: PageBlock): BlockActions = byBlock[pageBlock] ?: NoBlockActions
    }
}

private fun PageBlock.passageActions(
    chapterTitle: String?,
    toggleChrome: State<() -> Unit>,
    selected: State<(PassageSelection) -> Unit>,
    requested: State<(List<PassageSelection>) -> Unit>,
): BlockActions {
    fun sentenceOf(sentence: Sentence, paragraph: String) = selection(
        chapterTitle = chapterTitle,
        sentenceIndex = sentence.index,
        passage = sentence.text,
        paragraph = paragraph,
        unit = PassageUnit.Sentence,
    )

    return PassageActions(
        onSentence = { _, sentence, paragraph -> selected.value(sentenceOf(sentence, paragraph)) },

        // A word travels with the paragraph around it, exactly as a sentence
        // does. On its own it is a dictionary entry; what makes it a reading is
        // the clause it was taken out of.
        onWord = { _, sentence, word, paragraph ->
            selected.value(
                selection(
                    chapterTitle = chapterTitle,
                    sentenceIndex = sentence.index,
                    passage = word.text,
                    paragraph = paragraph,
                    unit = PassageUnit.Word,
                    wordIndex = word.index,
                ),
            )
        },

        // A paragraph is its own context, so it travels without one: there is
        // nothing around it left to send that is not already in it.
        onParagraph = { _, paragraph ->
            selected.value(
                selection(
                    chapterTitle = chapterTitle,
                    sentenceIndex = null,
                    passage = paragraph,
                    paragraph = paragraph,
                    unit = PassageUnit.Paragraph,
                ),
            )
        },

        onBlock = { _, sentences, paragraph ->
            requested.value(sentences.map { sentence -> sentenceOf(sentence, paragraph) })
        },

        onElsewhere = { toggleChrome.value() },
    ).forBlock(blockIndex)
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
