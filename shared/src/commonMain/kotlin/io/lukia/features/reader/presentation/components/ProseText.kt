package io.lukia.features.reader.presentation.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.toSize
import io.lukia.features.reader.domain.TextRun
import io.lukia.features.reader.presentation.TranslateOnScreen
import io.lukia.features.translate.domain.PassageUnit
import io.lukia.features.translate.domain.Sentence
import io.lukia.features.translate.domain.TranslationGestures
import io.lukia.features.translate.domain.Word
import io.lukia.features.translate.domain.splitIntoSentences
import io.lukia.features.translate.domain.words
import io.lukia.features.translate.presentation.SentenceTranslations
import io.lukia.features.translate.presentation.shownText

/** The space between one block and the next, as a multiple of the text size. */
private const val BLOCK_SPACING = 0.7f

// A paragraph of the book, and everything that happens when the reader points
// at part of it.

/**
 * A stretch of the book's prose, cut into sentences and words somebody can point
 * at.
 *
 * The sentences, the words and the set text are all remembered against what they
 * are built from, because a section is hundreds of these and none of the
 * splitting or the styling should happen again every time anything on the page
 * changes.
 *
 * Which passages are marked, and how, is drawn over the finished text rather
 * than written into it. That is what makes a tap feel like a tap: pointing at a
 * sentence changes nothing about the paragraph, so nothing is re-split, re-built
 * or re-laid out, and the mark is a repaint. Only the arrival of translated
 * words is a change to the text at all.
 *
 * When they do arrive the paragraph changes length, and everything below it has
 * to move. Two animations carry that: the text cross-fades between its two
 * settings, and the block eases into its new height - so the page settles rather
 * than jumping, and the eye can follow the line it was on to where it went.
 */
@Composable
internal fun ProseText(
    runs: List<TextRun>,
    style: TextStyle,
    translations: SentenceTranslations,
    actions: BlockActions,
    translateOnScreen: TranslateOnScreen,
    gestures: TranslationGestures,
    resizingType: Boolean,
    modifier: Modifier = Modifier,
) {
    val paragraph = remember(runs) { runs.plain() }
    val sentences = remember(paragraph) { splitIntoSentences(paragraph) }
    val words = remember(sentences) { sentences.words() }

    // Kept apart from the states themselves so that a sentence going from
    // untouched to waiting - which changes no words at all - does not count as
    // the paragraph having changed.
    val shownSentences = remember(sentences, translations) {
        buildMap {
            sentences.forEach { sentence ->
                translations[sentence.index].shownText?.let { put(sentence.index, it) }
            }
        }
    }

    val shownWords = remember(words, translations) {
        buildMap {
            words.forEach { word ->
                translations.words(word.sentenceIndex)[word.index].shownText?.let {
                    put(word.sentenceIndex to word.index, it)
                }
            }
        }
    }

    // A paragraph translated whole stands in for everything in it, so nothing
    // inside is looked up and nothing inside is on the page to be pointed at.
    val shownBlock = translations.block.shownText

    val drawn = remember(runs, sentences, words, shownBlock, shownSentences, shownWords) {
        buildParagraphText(
            runs = runs,
            sentences = sentences,
            words = words,
            block = shownBlock,
        ) { sentence, word ->
            if (word == null) {
                shownSentences[sentence.index]
            } else {
                shownWords[sentence.index to word.index]
            }
        }
    }

    val marked = passageMarkStyles(sentences, words, translations)
    val colors = passageMarkColors()

    // Held as state and read where it is drawn, never here: read during
    // composition it would recompose the paragraph on every frame of the sweep,
    // which is a paragraph re-measured sixty times a second to move a light
    // along it.
    val sweep = passageSweep(marked.anyWaiting)

    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    // Read at the moment of the tap rather than captured when the gesture was
    // set up, so the handler is not torn down and rebuilt every time anything
    // in the section recomposes.
    val currentDrawn by rememberUpdatedState(drawn)
    val currentActions by rememberUpdatedState(actions)

    // Whether this block is one of the ones the reader can see. It answers that
    // about itself rather than being told: nothing above it knows where its text
    // ended up - that is settled during layout, one block at a time - and a list
    // of every block's position kept somewhere central would be a second copy of
    // what the layout already knows, wrong for a frame every time anything moved.
    var onScreen by remember { mutableStateOf(false) }

    // What this block has already done. Starting at whatever is current means a
    // block scrolled into view after the reader asked stays out of that round,
    // which is right: it was not on screen when they asked.
    var actedOn by remember { mutableStateOf(translateOnScreen.generation) }

    LaunchedEffect(onScreen, translateOnScreen, sentences) {
        if (!onScreen || sentences.isEmpty()) return@LaunchedEffect
        if (!translateOnScreen.continuous && translateOnScreen.generation == actedOn) {
            return@LaunchedEffect
        }

        actedOn = translateOnScreen.generation
        currentActions.onBlock(sentences, paragraph)
    }

    val currentGestures by rememberUpdatedState(gestures)

    val tappable = modifier.pointerInput(sentences, words) {
        detectTapGestures(
            onTap = { position ->
                // A tap set to nothing is still a tap on the page, and a tap on
                // the page has always put the chrome away and brought it back.
                // Swallowing it would leave a reader who turned tapping off with
                // no way to reach the toolbar over their own text.
                if (currentGestures.tap == null) {
                    currentActions.onElsewhere()
                } else {
                    ask(currentGestures.tap, position, layout, currentDrawn, sentences, words) {
                        currentActions.act(it, paragraph)
                    }
                }
            },
            // Nothing for a long press that asks for nothing: holding a finger
            // on the page has never meant anything else, so there is nothing for
            // it to fall through to.
            onLongPress = { position ->
                ask(currentGestures.longPress, position, layout, currentDrawn, sentences, words) {
                    currentActions.act(it, paragraph)
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { onScreen = it.isOnScreen() }
            // Easing into its new height is what stops the rest of the chapter
            // from jumping the instant a translation lands.
            //
            // Left off while the type is being resized: a pinch changes every
            // block's height on every frame, and a height that eased towards
            // each of those in turn would trail the reader's fingers by a
            // quarter of a second all the way through the gesture.
            .then(
                if (resizingType) {
                    Modifier
                } else {
                    Modifier.animateContentSize(
                        animationSpec = tween(
                            durationMillis = TRANSLATION_SETTLE_MILLIS,
                            easing = TranslationEasing,
                        ),
                    )
                },
            ),
    ) {
        Crossfade(
            targetState = drawn,
            animationSpec = tween(TRANSLATION_FADE_MILLIS, easing = TranslationEasing),
            modifier = tappable,
        ) { paragraphText ->
            // Each copy measures itself. The outgoing one is still on screen
            // while the fade runs and its marks have to sit on its own lines,
            // not on the lines of the paragraph replacing it.
            var measured by remember { mutableStateOf<TextLayoutResult?>(null) }

            val marks = remember(paragraphText, marked) { marked.marksFor(paragraphText) }

            Text(
                text = paragraphText.text,
                style = style,
                // Both copies are on screen while the fade runs. Only the one
                // being faded in is what a tap should be measured against;
                // letting the outgoing copy report its layout would map taps
                // onto the paragraph that is on its way out.
                onTextLayout = {
                    measured = it
                    if (paragraphText === currentDrawn) layout = it
                },
                modifier = Modifier.drawBehind {
                    val lines = measured ?: return@drawBehind

                    drawPassageMarks(lines, marks, colors, sweep.value)
                },
            )
        }

        TranslatorsRemarks(sentences, words, translations, style)
    }
}

/**
 * What one gesture landed on, once it is known what that gesture asks for.
 *
 * [Nothing] is a gesture that reached no words - a tap in the gap between two
 * sentences, or past the end of the last line - which has always meant the page
 * rather than the text, and is how the chrome is put away and brought back.
 */
private sealed interface Pointed {

    data object Nothing : Pointed

    data class AtWord(val sentence: Sentence, val word: Word) : Pointed

    data class AtSentence(val sentence: Sentence) : Pointed

    data object AtParagraph : Pointed
}

/**
 * Works out what the reader pointed at, for the unit the gesture is set to.
 *
 * A gesture that is turned off reaches nothing at all rather than falling back
 * to something narrower: off is off, and a long press that quietly translated a
 * sentence because the word under it could not be found would be exactly the
 * kind of thing this setting exists to stop.
 *
 * The one place a gesture does not get what it asked for is a word it cannot
 * find - the punctuation between two words belongs to neither - and there it
 * gets nothing, not the sentence around it.
 */
private inline fun ask(
    unit: PassageUnit?,
    position: Offset,
    layout: TextLayoutResult?,
    drawn: ParagraphText,
    sentences: List<Sentence>,
    words: List<Word>,
    act: (Pointed) -> Unit,
) {
    if (unit == null) return

    val offset = layout?.getOffsetForPosition(position)
    val sentence = offset?.let { drawn.sentenceAt(it) }?.let { sentences.getOrNull(it) }

    // Nothing under the finger is the page, whatever the gesture was set to.
    if (offset == null || sentence == null) {
        act(Pointed.Nothing)
        return
    }

    when (unit) {
        PassageUnit.Paragraph -> act(Pointed.AtParagraph)

        PassageUnit.Sentence -> act(Pointed.AtSentence(sentence))

        PassageUnit.Word -> drawn.wordAt(offset)
            ?.let { words.at(it) }
            ?.let { act(Pointed.AtWord(sentence, it)) }
    }
}

/** Carries out what the reader asked of this block. */
private fun BlockActions.act(pointed: Pointed, paragraph: String) {
    when (pointed) {
        Pointed.Nothing -> onElsewhere()
        Pointed.AtParagraph -> onParagraph(paragraph)
        is Pointed.AtSentence -> onSentence(pointed.sentence, paragraph)
        is Pointed.AtWord -> onWord(pointed.sentence, pointed.word, paragraph)
    }
}

/** The word the book wrote at a place in the paragraph as it is currently drawn. */
private fun List<Word>.at(drawn: ParagraphText.DrawnWord): Word? =
    firstOrNull { it.sentenceIndex == drawn.sentenceIndex && it.index == drawn.span.index }

/** Whether any part of this is inside the window it is being drawn in. */
private fun LayoutCoordinates.isOnScreen(): Boolean {
    if (!isAttached) return false

    val bounds = boundsInRoot()
    if (bounds.isEmpty) return false

    return bounds.overlaps(Rect(Offset.Zero, findRootCoordinates().size.toSize()))
}

/**
 * How the page moves when a translation lands.
 *
 * A long tail out of a quick start: nearly all of the distance is covered at
 * once, and the last of it settles rather than stopping. That is the difference
 * between text that moved and text that was moved - and it is what lets the eye
 * stay on the line it was reading while the line travels.
 */
private val TranslationEasing: Easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

/**
 * How long the words take to change, and how long the block takes to reach its
 * new height.
 *
 * The words go first and the height follows, which is the order that reads as
 * one movement: text that has already changed sitting in a block still settling
 * looks like the page catching up with itself, while the reverse opens a gap
 * before anything arrives to fill it.
 */
private const val TRANSLATION_FADE_MILLIS = 180
private const val TRANSLATION_SETTLE_MILLIS = 260
