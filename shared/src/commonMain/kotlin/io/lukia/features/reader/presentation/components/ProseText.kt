package io.lukia.features.reader.presentation.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
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
import io.lukia.features.reader.presentation.TRANSLATE_SETTLE_MILLIS
import io.lukia.features.reader.presentation.TranslateOnScreen
import io.lukia.features.translate.domain.PassageUnit
import io.lukia.features.translate.domain.Sentence
import io.lukia.features.translate.domain.TranslationGestures
import io.lukia.features.translate.domain.Word
import io.lukia.features.translate.domain.splitIntoSentences
import io.lukia.features.translate.domain.words
import io.lukia.features.translate.presentation.SentenceTranslations
import io.lukia.features.translate.presentation.shownText
import io.lukia.shared.presentation.theme.LukiaMotion
import kotlinx.coroutines.delay

// A paragraph of the book, and everything that happens when the reader points
// at part of it.

/**
 * A stretch of the book's prose, cut into sentences and words somebody can point
 * at.
 *
 * The sentences, the words and the set text are all remembered against what they
 * are built from, because a book is thousands of these and none of the
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
    // on the page recomposes.
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

        // Cancelled along with this effect the moment the block leaves the
        // window, so a paragraph the reader scrolled past is never asked about -
        // see TRANSLATE_SETTLE_MILLIS.
        if (translateOnScreen.continuous) delay(TRANSLATE_SETTLE_MILLIS)

        actedOn = translateOnScreen.generation
        currentActions.onBlock(sentences, paragraph)
    }

    val currentGestures by rememberUpdatedState(gestures)

    // What the book wrote here, which is what a point in the drawn paragraph is
    // answered against - see ProsePassages.
    val passages = remember(sentences, words) { ProsePassages(sentences, words) }

    val tappable = modifier.pointerInput(passages) {
        detectReaderTaps(
            // A tap set to nothing reaches nothing, which leaves it as one of
            // the two taps that bring the chrome back - so a reader who turned
            // tapping off can still reach the toolbar over their own text.
            onTap = { position ->
                currentActions.act(
                    passages.at(currentGestures.tap, position, layout, currentDrawn),
                    paragraph,
                )
            },
            // Nothing for a long press that asks for nothing: holding a finger
            // on the page has never meant anything else, so there is nothing for
            // it to fall through to.
            onLongPress = { position ->
                currentActions.act(
                    passages.at(currentGestures.longPress, position, layout, currentDrawn),
                    paragraph,
                )
            },
            onChrome = { currentActions.onElsewhere() },
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
                            easing = LukiaMotion.Standard,
                        ),
                    )
                },
            ),
    ) {
        Crossfade(
            targetState = drawn,
            animationSpec = tween(TRANSLATION_FADE_MILLIS, easing = LukiaMotion.Standard),
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

/** What one gesture landed on, once it is known what that gesture asks for. */
private sealed interface Pointed {

    data class AtWord(val sentence: Sentence, val word: Word) : Pointed

    data class AtSentence(val sentence: Sentence) : Pointed

    data object AtParagraph : Pointed
}

/**
 * What one paragraph of the book is made of, and how a point in it becomes a
 * passage.
 *
 * The sentences and words are the ones the *book* wrote; where each of them
 * ended up in the paragraph as it is currently drawn is [ParagraphText]'s to
 * answer, and the two are brought together only here.
 */
private class ProsePassages(
    private val sentences: List<Sentence>,
    private val words: List<Word>,
) {

    /**
     * What the reader pointed at, for the unit the gesture is set to - or null
     * when the gesture reached nothing at all.
     *
     * A gesture that is turned off reaches nothing rather than falling back to
     * something narrower: off is off, and a long press that quietly translated a
     * sentence because the word under it could not be found would be exactly the
     * kind of thing that setting exists to stop.
     *
     * The one place a gesture does not get what it asked for is a word it cannot
     * find - the punctuation between two words belongs to neither - and there it
     * gets nothing, not the sentence around it.
     */
    fun at(
        unit: PassageUnit?,
        position: Offset,
        layout: TextLayoutResult?,
        drawn: ParagraphText,
    ): Pointed? {
        if (unit == null) return null

        val offset = layout?.getOffsetForPosition(position) ?: return null
        val pointed = drawn.sentenceAt(offset) ?: return null

        // A paragraph translated whole has none of its own sentences left on the
        // page - it is one span, filed under WHOLE_BLOCK - so a gesture inside it
        // is a gesture on the paragraph, whatever unit it was set to. Without
        // this, a tap meant to put the book's own words back looked up sentence
        // -1, found nothing, and was read as a tap on the page.
        if (pointed == WHOLE_BLOCK) return Pointed.AtParagraph

        val sentence = sentences.getOrNull(pointed) ?: return null

        return when (unit) {
            PassageUnit.Paragraph -> Pointed.AtParagraph

            PassageUnit.Sentence -> Pointed.AtSentence(sentence)

            PassageUnit.Word -> drawn.wordAt(offset)
                ?.let { words.at(it) }
                ?.let { Pointed.AtWord(sentence, it) }
        }
    }
}

/**
 * Carries out what the reader asked of this block, and says whether they asked
 * anything - which is what decides whether the gesture was answered by the text
 * or is one of the two that bring the chrome back.
 */
private fun BlockActions.act(pointed: Pointed?, paragraph: String): Boolean {
    when (pointed) {
        null -> return false
        Pointed.AtParagraph -> onParagraph(paragraph)
        is Pointed.AtSentence -> onSentence(pointed.sentence, paragraph)
        is Pointed.AtWord -> onWord(pointed.sentence, pointed.word, paragraph)
    }

    return true
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
 * How long the words take to change, and how long the block takes to reach its
 * new height.
 *
 * The words go first and the height follows, which is the order that reads as
 * one movement: text that has already changed sitting in a block still settling
 * looks like the page catching up with itself, while the reverse opens a gap
 * before anything arrives to fill it.
 *
 * Both are the app's own durations, on the app's own curve - a long tail out of
 * a quick start, which is what lets the eye stay on the line it was reading
 * while the line travels. See [LukiaMotion], which is where every other timing
 * in the app comes from; a page re-setting itself is not a special case.
 */
private const val TRANSLATION_FADE_MILLIS = LukiaMotion.QuickMillis
private const val TRANSLATION_SETTLE_MILLIS = LukiaMotion.StandardMillis
