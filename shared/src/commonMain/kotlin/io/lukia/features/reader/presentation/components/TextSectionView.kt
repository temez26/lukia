package io.lukia.features.reader.presentation.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import io.lukia.core.presentation.displayShortMessage
import io.lukia.shared.presentation.components.LukiaDivider
import io.lukia.features.reader.domain.TextBlock
import io.lukia.features.reader.domain.TextRun
import io.lukia.features.reader.domain.TextSection
import io.lukia.features.reader.presentation.TranslateOnScreen
import io.lukia.features.translate.domain.Sentence
import io.lukia.features.translate.domain.Word
import io.lukia.features.translate.domain.splitIntoSentences
import io.lukia.features.translate.domain.words
import io.lukia.features.translate.presentation.PassageTranslation
import io.lukia.features.translate.presentation.SectionTranslations
import io.lukia.features.translate.presentation.SentenceTranslations
import io.lukia.features.translate.presentation.shownText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The space between one block and the next, as a multiple of the text size. */
private const val BLOCK_SPACING = 0.7f

/** What a quotation and a list item are set in from the edge of the text. */
private val IndentWidth: Dp = 20.dp

/** Room for the widest marker a list is likely to have, so the text lines up. */
private val MarkerWidth: Dp = 28.dp

/**
 * What a reader can ask of the passages in one section.
 *
 * Four callbacks in one value because they always travel together and always
 * come from the same place, and because a paragraph inside a quotation inside a
 * section should not have to name all four at every hop on the way down.
 *
 * The block index is filled in on the way past, so nothing below the section has
 * to know where it sits in one.
 */
@Stable
internal class PassageActions(
    /** A tap: the sentence under the finger. */
    val onSentence: (blockIndex: Int, sentence: Sentence, paragraph: String) -> Unit =
        { _, _, _ -> },
    /** A long press: the single word under it. */
    val onWord: (blockIndex: Int, sentence: Sentence, word: Word, paragraph: String) -> Unit =
        { _, _, _, _ -> },
    /** Everything in a block, when the reader asks for what is on screen. */
    val onBlock: (blockIndex: Int, sentences: List<Sentence>, paragraph: String) -> Unit =
        { _, _, _ -> },
    /** A tap that landed on no words, which is a tap on the page rather than on text. */
    val onElsewhere: () -> Unit = {}
) {

    /** The same actions, with the block already named. */
    fun forBlock(blockIndex: Int) = BlockActions(
        onSentence = { sentence, paragraph -> onSentence(blockIndex, sentence, paragraph) },
        onWord = { sentence, word, paragraph -> onWord(blockIndex, sentence, word, paragraph) },
        onBlock = { sentences, paragraph -> onBlock(blockIndex, sentences, paragraph) },
        onElsewhere = onElsewhere
    )

    companion object {
        val None = PassageActions()
    }
}

/** [PassageActions] once it knows which block it belongs to. */
internal class BlockActions(
    val onSentence: (Sentence, paragraph: String) -> Unit,
    val onWord: (Sentence, Word, paragraph: String) -> Unit,
    val onBlock: (List<Sentence>, paragraph: String) -> Unit,
    val onElsewhere: () -> Unit
)

/**
 * One section of a reflowable book, set at the reader's chosen size.
 *
 * Everything here is measured against [style] rather than given a size of its
 * own: a heading is the body size scaled up, the space between blocks is a
 * proportion of the leading. That is what makes the text size setting act on the
 * whole page instead of only on its paragraphs - a book at 200% with the same
 * gaps it had at 100% reads as text that has outgrown its own layout.
 *
 * [translations] is what has become of the section's own sentences, and
 * [actions] is how a passage somebody pointed at gets back to whoever can
 * translate it. Both default to nothing, so this composable can still be drawn -
 * in a test, or in a preview - with no translator behind it.
 *
 * Each block is handed only its own slice. That is what keeps translating one
 * sentence from being work for the rest of the chapter: a block whose slice
 * compares equal to the one it was given last time is not composed again at all.
 */
@Composable
internal fun TextSectionView(
    section: TextSection,
    style: TextStyle,
    modifier: Modifier = Modifier,
    translations: SectionTranslations = SectionTranslations.None,
    actions: PassageActions = PassageActions.None,
    /** Whether what is on screen should be translated without being asked twice. */
    translateOnScreen: TranslateOnScreen = TranslateOnScreen.Idle,
    /**
     * Whether the type is being resized right now - a pinch in progress.
     *
     * While it is, a block changing height is the reader's fingers rather than a
     * translation landing, and the two want opposite things from an animation.
     */
    resizingType: Boolean = false
) {
    val spacing = with(LocalDensity.current) {
        (style.fontSize * BLOCK_SPACING).toDp()
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(spacing),
        modifier = modifier
    ) {
        section.blocks.forEachIndexed { index, block ->
            TextBlockView(
                block = block,
                style = style,
                translations = translations.block(index),
                actions = actions.forBlock(index),
                translateOnScreen = translateOnScreen,
                resizingType = resizingType
            )
        }
    }
}

@Composable
private fun TextBlockView(
    block: TextBlock,
    style: TextStyle,
    translations: SentenceTranslations,
    actions: BlockActions,
    translateOnScreen: TranslateOnScreen,
    resizingType: Boolean
) {
    when (block) {
        is TextBlock.Heading ->
            HeadingBlock(block, style, translations, actions, translateOnScreen, resizingType)

        is TextBlock.Paragraph -> ProseText(
            runs = block.text,
            style = style,
            translations = translations,
            actions = actions,
            translateOnScreen = translateOnScreen,
            resizingType = resizingType
        )

        is TextBlock.ListItem ->
            ListItemBlock(block, style, translations, actions, translateOnScreen, resizingType)

        is TextBlock.Quote ->
            QuoteBlock(block, style, translations, actions, translateOnScreen, resizingType)
        // Preformatted text is not prose. Splitting code at its full stops would
        // offer to translate `object.method()` as a sentence.
        is TextBlock.Code -> CodeBlock(block, style)
        is TextBlock.Figure -> FigureBlock(block)
        TextBlock.Separator -> SeparatorBlock()
    }
}

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
private fun ProseText(
    runs: List<TextRun>,
    style: TextStyle,
    translations: SentenceTranslations,
    actions: BlockActions,
    translateOnScreen: TranslateOnScreen,
    resizingType: Boolean,
    modifier: Modifier = Modifier
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

    val drawn = remember(runs, sentences, words, shownSentences, shownWords) {
        buildParagraphText(runs = runs, sentences = sentences, words = words) { sentence, word ->
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

    val currentTranslations by rememberUpdatedState(translations)

    val tappable = modifier.pointerInput(sentences, words) {
        detectTapGestures(
            onTap = { position ->
                val offset = layout?.getOffsetForPosition(position)

                // A tap on a passage the reader has already asked about acts on
                // that passage. Without this, tapping a word they had just
                // translated - which is exactly where the marks invite a second
                // tap - would translate the whole sentence around it instead of
                // giving them the word back.
                //
                // Only a word that is marked. An untouched word is part of the
                // sentence it is in, and tapping it means what it has always
                // meant.
                val marked = offset
                    ?.let { currentDrawn.wordAt(it) }
                    ?.takeIf { currentTranslations.words(it.sentenceIndex)[it.span.index] != null }
                    ?.let { words.at(it) }

                val sentence = offset
                    ?.let { currentDrawn.sentenceAt(it) }
                    ?.let { sentences.getOrNull(it) }

                when {
                    marked != null && sentence != null ->
                        currentActions.onWord(sentence, marked, paragraph)

                    sentence != null -> currentActions.onSentence(sentence, paragraph)

                    // A tap between two sentences, or past the end of the last
                    // line, is a tap on the page rather than on any words - and
                    // putting the chrome away is what that has always meant.
                    else -> currentActions.onElsewhere()
                }
            },
            // Holding a word asks about that word alone, whether or not it has
            // been asked about before. A tap on an untouched word still means
            // the sentence, so the two never compete: the finger that stops is
            // asking the narrower question.
            onLongPress = { position ->
                val offset = layout?.getOffsetForPosition(position) ?: return@detectTapGestures
                val word = currentDrawn.wordAt(offset)?.let { words.at(it) }
                    ?: return@detectTapGestures
                val sentence = sentences.getOrNull(word.sentenceIndex)
                    ?: return@detectTapGestures

                currentActions.onWord(sentence, word, paragraph)
            }
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
                            easing = TranslationEasing
                        )
                    )
                }
            )
    ) {
        Crossfade(
            targetState = drawn,
            animationSpec = tween(TRANSLATION_FADE_MILLIS, easing = TranslationEasing),
            modifier = tappable
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
                }
            )
        }

        TranslatorsRemarks(sentences, words, translations, style)
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
 * What the translator had to say about this paragraph, under it.
 *
 * Two different things share this line, because they belong in the same place:
 * a remark about an ambiguous word, and the reason a passage could not be
 * translated at all. Under rather than beside, because a note set into the prose
 * is a footnote in the middle of a sentence and the reader is here to read the
 * book.
 *
 * A failure has to appear somewhere. Marked only by a pair of dots it says
 * nothing about what went wrong, which leaves somebody looking at a mark and
 * wondering whether anything is happening.
 *
 * Single words have both as much as sentences do - a word asked about on its own
 * is usually the ambiguous one, so its note is the one most worth reading - and
 * they are gathered in reading order with the sentences they sit in rather than
 * in a list of their own.
 */
@Composable
private fun TranslatorsRemarks(
    sentences: List<Sentence>,
    words: List<Word>,
    translations: SentenceTranslations,
    style: TextStyle
) {
    val remarks = remember(sentences, words, translations) {
        buildList {
            sentences.forEach { sentence ->
                translations[sentence.index].remark()?.let { add(it) }

                // A word is only on the page while its sentence's own words are,
                // and a remark about something nobody can see is noise.
                if (translations[sentence.index].shownText != null) return@forEach

                words.forEach { word ->
                    if (word.sentenceIndex != sentence.index) return@forEach

                    translations.words(sentence.index)[word.index]
                        .remark(replaced = word.text)
                        ?.let { add(it) }
                }
            }
        }
    }

    if (remarks.isEmpty()) return

    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(top = 6.dp)
    ) {
        remarks.forEach { remark ->
            Text(
                text = remark.text,
                style = style.copy(
                    fontSize = style.fontSize * NOTE_SCALE,
                    lineHeight = style.fontSize * NOTE_SCALE * 1.35f,
                    fontStyle = if (remark.failed) FontStyle.Normal else FontStyle.Italic,
                    color = if (remark.failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            )
        }
    }
}

/**
 * What is worth saying under the paragraph about one passage, if anything.
 *
 * [replaced] is the book's own word, named for a note about a single word
 * because that word is no longer on the page - the translation is standing where
 * it was, and a remark about "piece" under a paragraph now reading "ase" is a
 * remark about nothing the reader can see. A failure needs no such introduction:
 * nothing was replaced, so the word is still there to look at.
 */
private fun PassageTranslation?.remark(replaced: String? = null): Remark? = when (this) {
    null, is PassageTranslation.Loading -> null

    is PassageTranslation.Failed -> Remark(error.displayShortMessage(), failed = true)

    is PassageTranslation.Ready -> takeIf { !showingOriginal }
        ?.translation
        ?.note
        ?.let { note ->
            Remark(if (replaced == null) note else "$replaced: $note", failed = false)
        }
}

private data class Remark(val text: String, val failed: Boolean)

/**
 * Which passages of this paragraph are marked, and how - named by index rather
 * than by where they ended up on the page.
 *
 * By index because two settings of the paragraph are on screen at once while a
 * translation fades in, and a mark has to be placed against whichever of them it
 * is being drawn over. What is the same for both is *which* sentence and which
 * word are marked; where those sit is each copy's own business, which is what
 * [marksFor] settles.
 *
 * A passage nobody has touched is left out entirely, which is what makes an
 * untranslated paragraph cost nothing to draw.
 */
private class MarkedPassages(
    private val sentences: Map<Int, PassageMarkStyle>,
    private val words: Map<Pair<Int, Int>, PassageMarkStyle>
) {

    val anyWaiting: Boolean = sentences.values.any { it == PassageMarkStyle.Waiting } ||
        words.values.any { it == PassageMarkStyle.Waiting }

    private val isEmpty: Boolean get() = sentences.isEmpty() && words.isEmpty()

    fun marksFor(text: ParagraphText): List<PassageMark> {
        if (isEmpty) return emptyList()

        return buildList {
            text.sentences.forEach { span ->
                sentences[span.index]?.let { add(PassageMark(span.start, span.end, it)) }
            }

            text.words.forEach { word ->
                words[word.sentenceIndex to word.span.index]?.let {
                    add(PassageMark(word.span.start, word.span.end, it))
                }
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        other is MarkedPassages && sentences == other.sentences && words == other.words

    override fun hashCode(): Int = 31 * sentences.hashCode() + words.hashCode()
}

@Composable
private fun passageMarkStyles(
    sentences: List<Sentence>,
    words: List<Word>,
    translations: SentenceTranslations
): MarkedPassages = remember(sentences, words, translations) {
    MarkedPassages(
        sentences = buildMap {
            sentences.forEach { sentence ->
                translations[sentence.index].markStyle()?.let { put(sentence.index, it) }
            }
        },
        words = buildMap {
            words.forEach { word ->
                translations.words(word.sentenceIndex)[word.index].markStyle()?.let {
                    put(word.sentenceIndex to word.index, it)
                }
            }
        }
    )
}

@Composable
private fun HeadingBlock(
    block: TextBlock.Heading,
    style: TextStyle,
    translations: SentenceTranslations,
    actions: BlockActions,
    translateOnScreen: TranslateOnScreen,
    resizingType: Boolean
) {
    val scale = when (block.level) {
        1 -> 1.6f
        2 -> 1.35f
        3 -> 1.18f
        else -> 1.05f
    }

    ProseText(
        runs = block.text,
        style = style.copy(
            fontSize = style.fontSize * scale,
            // Headings are set tighter than prose: the leading that makes a
            // paragraph readable makes a two-line title look like two titles.
            lineHeight = style.fontSize * scale * 1.25f,
            fontWeight = FontWeight.SemiBold
        ),
        translations = translations,
        actions = actions,
        translateOnScreen = translateOnScreen,
        resizingType = resizingType,
        modifier = Modifier
            .fillMaxWidth()
            // Bound to what it introduces rather than floating between two
            // blocks, so a heading sits with the text below it.
            .padding(top = 8.dp)
            .semantics { heading() }
    )
}

@Composable
private fun ListItemBlock(
    block: TextBlock.ListItem,
    style: TextStyle,
    translations: SentenceTranslations,
    actions: BlockActions,
    translateOnScreen: TranslateOnScreen,
    resizingType: Boolean
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = block.marker,
            style = style,
            modifier = Modifier
                .padding(start = IndentWidth)
                .width(MarkerWidth)
        )

        ProseText(
            runs = block.text,
            style = style,
            translations = translations,
            actions = actions,
            translateOnScreen = translateOnScreen,
            resizingType = resizingType,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * A quotation, marked by a rule down its start edge as well as by the indent.
 * The indent alone is not enough on a narrow screen, where a paragraph set in
 * twenty points from the edge just looks like a paragraph.
 */
@Composable
private fun QuoteBlock(
    block: TextBlock.Quote,
    style: TextStyle,
    translations: SentenceTranslations,
    actions: BlockActions,
    translateOnScreen: TranslateOnScreen,
    resizingType: Boolean
) {
    // The row takes the height its text needs, which is what lets the rule
    // beside it fill that height rather than collapsing to nothing.
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .padding(end = IndentWidth)
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outlineVariant)
        )

        ProseText(
            runs = block.text,
            style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            translations = translations,
            actions = actions,
            translateOnScreen = translateOnScreen,
            resizingType = resizingType,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Preformatted text, which is the one thing in a book that must not be re-wrapped
 * - so it scrolls sideways instead, and the book behind it does not.
 */
@Composable
private fun CodeBlock(block: TextBlock.Code, style: TextStyle) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = block.text,
            style = style.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = style.fontSize * 0.88f,
                lineHeight = style.fontSize * 1.3f
            ),
            softWrap = false
        )
    }
}

/**
 * An illustration, decoded only once it is on screen.
 *
 * A book can carry hundreds of these, and decoding them all when it opens would
 * cost more memory than every word in it. Held as bytes until here, and dropped
 * again by Compose when the section scrolls far enough away to be discarded.
 *
 * A figure that cannot be decoded draws nothing rather than a broken-image mark:
 * the book is still perfectly readable without it, and a row of error icons down
 * a chapter is worse than a chapter with a gap.
 */
@Composable
private fun FigureBlock(block: TextBlock.Figure) {
    val image by produceState<ImageBitmap?>(initialValue = null, block) {
        value = withContext(Dispatchers.Default) {
            runCatching { block.bytes.decodeToImageBitmap() }.getOrNull()
        }
    }

    image?.let { bitmap ->
        Image(
            bitmap = bitmap,
            contentDescription = block.description?.takeIf { it.isNotBlank() },
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** A scene break: a short rule rather than a line across the whole measure. */
@Composable
private fun SeparatorBlock() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        LukiaDivider(modifier = Modifier.fillMaxWidth(0.3f))
    }
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

/** A note is an aside about the text, and is set smaller than the text. */
private const val NOTE_SCALE = 0.82f
