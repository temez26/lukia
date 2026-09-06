package io.lukia.features.reader.presentation.components.text

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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.toSize
import io.lukia.core.domain.text.PassageUnit
import io.lukia.features.reader.domain.text.TextRun
import io.lukia.features.reader.domain.text.splitIntoSentences
import io.lukia.features.reader.domain.text.words
import io.lukia.features.reader.presentation.components.chrome.detectReaderTaps
import io.lukia.features.reader.presentation.components.passages.BlockActions
import io.lukia.features.reader.presentation.components.passages.PassageBands
import io.lukia.features.reader.presentation.components.passages.WordGesture
import io.lukia.features.reader.presentation.components.passages.drawPassageBands
import io.lukia.features.reader.presentation.components.passages.passageMarkColors
import io.lukia.features.reader.presentation.components.passages.passageMarkStyles
import io.lukia.features.reader.presentation.components.passages.passageSweep
import io.lukia.features.reader.presentation.components.translation.TranslatorsRemarks
import io.lukia.features.reader.presentation.translation.SentenceTranslations
import io.lukia.features.reader.presentation.translation.TRANSLATE_SETTLE_MILLIS
import io.lukia.features.reader.presentation.translation.TranslateOnScreen
import io.lukia.features.reader.presentation.translation.shownText
import io.lukia.features.translate.domain.settings.TranslationGestures
import io.lukia.resources.Res
import io.lukia.resources.reader_restore_this_paragraph
import io.lukia.resources.reader_translate_this_paragraph
import io.lukia.shared.presentation.theme.LukiaMotion
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

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
 *
 * Every one of those gestures is a point on the page, and a point on the page is
 * the one thing a reader working by voice, by switch or by keyboard does not
 * have. So the paragraph also carries the ask as an action of its own - see
 * [translationAction]. It is the whole block rather than the sentence under the
 * finger, because the finger is what named the sentence; the block is the
 * largest passage that is unambiguous without one, and it is already the unit
 * the reader's own "translate what is on screen" works in.
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

    // Read at the moment of the hold rather than keyed into the gesture
    // handler: which words have been answered changes with every translation
    // that lands, and rebuilding the handler for each of them would tear down
    // a pointer input the reader may have a finger on.
    val currentTranslations by rememberUpdatedState(translations)

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
            // Nothing for a hold that asks for nothing: holding a finger on the
            // page has never meant anything else, so there is nothing for it to
            // fall through to.
            onHold = { position ->
                currentActions.act(
                    passages.held(
                        position = position,
                        layout = layout,
                        drawn = currentDrawn,
                        translations = currentTranslations,
                        gestures = currentGestures,
                    ),
                    paragraph,
                    WordGesture.Held,
                )
            },
            // Always the word, whatever the hold is set to and whether or not it
            // has been translated. The question this gesture asks is about the
            // word rather than about the passage it sits in, and it is the same
            // question every time.
            onQuestion = { position ->
                currentActions.act(
                    passages.at(PassageUnit.Word, position, layout, currentDrawn),
                    paragraph,
                    WordGesture.Questioned,
                )
            },
            onChrome = { currentActions.onElsewhere() },
        )
    }

    val translate = translationAction(translations) {
        currentActions.onParagraph(paragraph)
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

            // Where those marks land, kept across the frames of a sweep - see
            // [PassageBands]. The layout it is worked out from is read in the
            // draw block below and nowhere else, so a re-layout still costs a
            // repaint rather than a recomposition.
            val bands = remember { PassageBands() }
            val density = LocalDensity.current

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
                // The action sits on the words rather than on the block around
                // them. A screen reader stops on nodes that speak, and the
                // paragraph is the one that does - an action hung on the column
                // holding it belongs to a node nothing ever lands on, which is
                // an action nobody can reach.
                modifier = Modifier
                    .semantics { customActions = listOf(translate) }
                    .drawBehind {
                        val lines = measured ?: return@drawBehind

                        drawPassageBands(bands.of(density, lines, marks), colors, sweep.value)
                    },
            )
        }

        TranslatorsRemarks(sentences, words, translations, style)
    }
}

/**
 * The one thing a paragraph can be asked without pointing at part of it: translate
 * it, or put the book's own words back.
 *
 * One action rather than two, named for what pressing it will do, because that is
 * what the gesture underneath already is - asking again about a passage that has
 * been answered is how a reader undoes it, and an action list offering both
 * directions at once would describe a control the app does not have.
 *
 * A custom action rather than a click. A click on a paragraph of a book means
 * something already - it is how the chrome comes back - and a paragraph that
 * announced itself as a button would be nine hundred buttons in a chapter.
 */
@Composable
private fun translationAction(
    translations: SentenceTranslations,
    onAct: () -> Unit,
): CustomAccessibilityAction {
    val label = stringResource(
        if (translations.block.shownText == null) {
            Res.string.reader_translate_this_paragraph
        } else {
            Res.string.reader_restore_this_paragraph
        },
    )

    // Keyed on the label alone. What the action does is read at the moment it is
    // invoked rather than captured here, for the reason the tap handlers above
    // are: the lambda is rebuilt on every composition of a paragraph, and a
    // chapter is hundreds of these being composed as it scrolls past.
    val current by rememberUpdatedState(onAct)

    return remember(label) {
        CustomAccessibilityAction(label) {
            current()
            true
        }
    }
}

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
