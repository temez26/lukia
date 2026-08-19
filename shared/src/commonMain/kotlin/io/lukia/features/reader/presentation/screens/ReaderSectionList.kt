package io.lukia.features.reader.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.domain.TextSection
import io.lukia.features.reader.presentation.TranslateOnScreen
import io.lukia.features.reader.presentation.components.PassageActions
import io.lukia.features.reader.presentation.components.TextSectionView
import io.lukia.features.reader.presentation.theme.readerTextStyle
import io.lukia.features.translate.domain.PassageUnit
import io.lukia.features.translate.domain.Sentence
import io.lukia.features.translate.domain.TextAnchor
import io.lukia.features.translate.domain.TranslationGestures
import io.lukia.features.translate.presentation.PassageSelection
import io.lukia.features.translate.presentation.ReaderTranslations
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.LukiaElevation
import kotlinx.coroutines.flow.map

/**
 * A reflowable book, section by section.
 *
 * The same scrolling list of sheets a PDF gets, so moving between a PDF and an
 * EPUB is not moving between two different readers - but what a sheet holds is
 * text this app has set, so the sheet is as tall as its text needs and the
 * settings act on the words rather than on a picture of them.
 *
 * The measure never exceeds the window. Above 100% a PDF page is deliberately
 * drawn wider than the space available and panned, because that is the only way
 * to get closer to type whose size was fixed when the file was made; text has no
 * such problem, and a line of prose running off both edges of the window is not
 * a way to read anything. So the width presets set the measure up to the width
 * available and stop there, and getting closer is what the text size is for -
 * which is also what a pinch does here.
 */
@Composable
internal fun SectionList(
    sections: List<TextSection>,
    listState: LazyListState,
    settings: ReaderSettings,
    pageColor: Color,
    translations: ReaderTranslations,
    onToggleChrome: () -> Unit,
    onSettingsChange: (ReaderSettings) -> Unit,
    onPassageSelected: (PassageSelection) -> Unit,
    onPassagesRequested: (List<PassageSelection>) -> Unit,
    translateOnScreen: TranslateOnScreen,
    gestures: TranslationGestures,
) {
    val dimens = LocalLukiaDimens.current

    // Held here while a pinch is in progress and committed when it ends, so the
    // type follows the fingers without every intermediate size being written to
    // storage.
    var pinchScale by remember { mutableStateOf<Float?>(null) }
    val effectiveScale = pinchScale ?: settings.textScale

    // Built from the size the fingers are at rather than the size that has been
    // stored, which is what makes the whole page re-set as the pinch happens.
    val style = readerTextStyle(settings.withTextScale(effectiveScale))

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columnWidth = if (dimens.maxReaderWidth == Dp.Unspecified) {
            maxWidth
        } else {
            minOf(maxWidth, dimens.maxReaderWidth)
        }

        val sheetWidth = minOf(columnWidth * settings.pageScale, maxWidth)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pinchToZoom(
                    scale = effectiveScale,
                    range = ReaderSettings.MIN_TEXT_SCALE..ReaderSettings.MAX_TEXT_SCALE,
                    onPinch = { pinchScale = it },
                    onPinchEnd = {
                        pinchScale?.let { onSettingsChange(settings.withTextScale(it)) }
                        pinchScale = null
                    },
                )
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onToggleChrome() })
                },
        ) {
            LazyColumn(
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PageGap),
                contentPadding = PaddingValues(
                    top = chromeHeight(),
                    bottom = PageListPadding,
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(count = sections.size) { index ->
                    Box(
                        modifier = Modifier
                            .width(sheetWidth)
                            .shadow(elevation = LukiaElevation.page)
                            .background(pageColor)
                            .padding(SheetPadding)
                            // Unlike a rendered page there is real text behind
                            // this, so the description says where in the book it
                            // is and leaves the reading to the text itself.
                            .semantics {
                                contentDescription =
                                    "Section ${index + 1} of ${sections.size}"
                            },
                    ) {
                        val section = sections[index]

                        TextSectionView(
                            section = section,
                            style = style,
                            translations = translations.section(index),
                            translateOnScreen = translateOnScreen,
                            gestures = gestures,
                            resizingType = pinchScale != null,
                            actions = rememberSectionActions(
                                sectionIndex = index,
                                chapterTitle = section.title,
                                onToggleChrome = onToggleChrome,
                                onPassageSelected = onPassageSelected,
                                onPassagesRequested = onPassagesRequested,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * What a tap, a long press and a call for the whole page mean in one section.
 *
 * Remembered per section rather than rebuilt every composition, because a book
 * is hundreds of blocks and a fresh set of callbacks for each of them on every
 * recomposition would make every one of those blocks recompose too - which is
 * the exact cost the sliced translations exist to avoid.
 *
 * The callbacks are read through [rememberUpdatedState] rather than captured,
 * so remembering these does not pin the reader of a moment ago.
 */
@Composable
private fun rememberSectionActions(
    sectionIndex: Int,
    chapterTitle: String?,
    onToggleChrome: () -> Unit,
    onPassageSelected: (PassageSelection) -> Unit,
    onPassagesRequested: (List<PassageSelection>) -> Unit,
): PassageActions {
    val toggleChrome = rememberUpdatedState(onToggleChrome)
    val selected = rememberUpdatedState(onPassageSelected)
    val requested = rememberUpdatedState(onPassagesRequested)

    return remember(sectionIndex, chapterTitle) {
        fun selection(
            blockIndex: Int,
            sentenceIndex: Int?,
            wordIndex: Int?,
            passage: String,
            paragraph: String,
            unit: PassageUnit,
        ) = PassageSelection(
            anchor = TextAnchor.Section(
                sectionIndex = sectionIndex,
                blockIndex = blockIndex,
                sentenceIndex = sentenceIndex,
                wordIndex = wordIndex,
            ),
            passage = passage,
            chapterTitle = chapterTitle,
            surroundingText = paragraph,
            unit = unit,
        )

        PassageActions(
            onSentence = { block, sentence, paragraph ->
                selected.value(
                    selection(
                        blockIndex = block,
                        sentenceIndex = sentence.index,
                        wordIndex = null,
                        passage = sentence.text,
                        paragraph = paragraph,
                        unit = PassageUnit.Sentence,
                    ),
                )
            },
            // A word travels with the paragraph around it, exactly as a sentence
            // does. On its own it is a dictionary entry; what makes it a reading
            // is the clause it was taken out of.
            onWord = { block, sentence, word, paragraph ->
                selected.value(
                    selection(
                        blockIndex = block,
                        sentenceIndex = sentence.index,
                        wordIndex = word.index,
                        passage = word.text,
                        paragraph = paragraph,
                        unit = PassageUnit.Word,
                    ),
                )
            },
            // A paragraph is its own context, so it travels without one: there
            // is nothing around it left to send that is not already in it.
            onParagraph = { block, paragraph ->
                selected.value(
                    selection(
                        blockIndex = block,
                        sentenceIndex = null,
                        wordIndex = null,
                        passage = paragraph,
                        paragraph = paragraph,
                        unit = PassageUnit.Paragraph,
                    ),
                )
            },
            onBlock = { block, sentences, paragraph ->
                requested.value(
                    sentences.map { sentence ->
                        selection(
                            blockIndex = block,
                            sentenceIndex = sentence.index,
                            wordIndex = null,
                            passage = sentence.text,
                            paragraph = paragraph,
                            unit = PassageUnit.Sentence,
                        )
                    },
                )
            },
            onElsewhere = { toggleChrome.value() },
        )
    }
}
