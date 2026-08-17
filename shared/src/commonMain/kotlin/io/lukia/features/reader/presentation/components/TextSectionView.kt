package io.lukia.features.reader.presentation.components

import androidx.compose.animation.Crossfade
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.presentation.displayShortMessage
import io.lukia.shared.presentation.components.LukiaDivider
import io.lukia.features.reader.domain.TextBlock
import io.lukia.features.reader.domain.TextRun
import io.lukia.features.reader.domain.TextSection
import io.lukia.features.translate.domain.Sentence
import io.lukia.features.translate.domain.splitIntoSentences
import io.lukia.features.translate.presentation.PassageTranslation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The space between one block and the next, as a multiple of the text size. */
private const val BLOCK_SPACING = 0.7f

/** What a quotation and a list item are set in from the edge of the text. */
private val IndentWidth: Dp = 20.dp

/** Room for the widest marker a list is likely to have, so the text lines up. */
private val MarkerWidth: Dp = 28.dp

/**
 * One section of a reflowable book, set at the reader's chosen size.
 *
 * Everything here is measured against [style] rather than given a size of its
 * own: a heading is the body size scaled up, the space between blocks is a
 * proportion of the leading. That is what makes the text size setting act on the
 * whole page instead of only on its paragraphs - a book at 200% with the same
 * gaps it had at 100% reads as text that has outgrown its own layout.
 *
 * [translationOf] is asked about every sentence the section draws, and
 * [onSentenceTapped] is how a sentence somebody pointed at gets back to whoever
 * can translate it. Both default to doing nothing, so this composable can still
 * be drawn - in a test, or in a preview - with no translator behind it.
 */
@Composable
internal fun TextSectionView(
    section: TextSection,
    style: TextStyle,
    modifier: Modifier = Modifier,
    translationOf: (blockIndex: Int, sentenceIndex: Int) -> PassageTranslation? = { _, _ -> null },
    onSentenceTapped: (blockIndex: Int, sentence: Sentence, paragraph: String) -> Unit =
        { _, _, _ -> },
    /** A tap that landed on no sentence, which is a tap on the page rather than on words. */
    onTapElsewhere: () -> Unit = {}
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
                translationOf = { sentence -> translationOf(index, sentence) },
                onSentenceTapped = { sentence, paragraph ->
                    onSentenceTapped(index, sentence, paragraph)
                },
                onTapElsewhere = onTapElsewhere
            )
        }
    }
}

@Composable
private fun TextBlockView(
    block: TextBlock,
    style: TextStyle,
    translationOf: (sentenceIndex: Int) -> PassageTranslation?,
    onSentenceTapped: (Sentence, paragraph: String) -> Unit,
    onTapElsewhere: () -> Unit
) {
    when (block) {
        is TextBlock.Heading ->
            HeadingBlock(block, style, translationOf, onSentenceTapped, onTapElsewhere)

        is TextBlock.Paragraph -> ProseText(
            runs = block.text,
            style = style,
            translationOf = translationOf,
            onSentenceTapped = onSentenceTapped,
            onTapElsewhere = onTapElsewhere
        )

        is TextBlock.ListItem ->
            ListItemBlock(block, style, translationOf, onSentenceTapped, onTapElsewhere)

        is TextBlock.Quote ->
            QuoteBlock(block, style, translationOf, onSentenceTapped, onTapElsewhere)
        // Preformatted text is not prose. Splitting code at its full stops would
        // offer to translate `object.method()` as a sentence.
        is TextBlock.Code -> CodeBlock(block, style)
        is TextBlock.Figure -> FigureBlock(block)
        TextBlock.Separator -> SeparatorBlock()
    }
}

/**
 * A stretch of the book's prose, cut into sentences somebody can point at.
 *
 * The sentences and the set text are both remembered against what they are
 * built from, because a section is hundreds of these and neither the splitting
 * nor the styling should happen again every time anything on the page changes.
 *
 * The crossfade is on the whole paragraph rather than on the sentence inside it:
 * a translated sentence is rarely the same length as the original, so the lines
 * under it move whatever is done, and fading the block through the change reads
 * as the paragraph re-setting itself rather than as text jumping.
 */
@Composable
private fun ProseText(
    runs: List<TextRun>,
    style: TextStyle,
    translationOf: (sentenceIndex: Int) -> PassageTranslation?,
    onSentenceTapped: (Sentence, paragraph: String) -> Unit,
    onTapElsewhere: () -> Unit,
    modifier: Modifier = Modifier
) {
    val paragraph = remember(runs) { runs.plain() }
    val sentences = remember(paragraph) { splitIntoSentences(paragraph) }

    val marked = rememberSentenceMarks(sentences.map { translationOf(it.index) })

    val drawn = remember(runs, sentences, marked) {
        buildParagraphText(runs = runs, sentences = sentences) { sentence ->
            marked[sentence.index]
        }
    }

    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    // Read at the moment of the tap rather than captured when the gesture was
    // set up, so the handler is not torn down and rebuilt every time anything
    // in the section recomposes.
    val currentDrawn by rememberUpdatedState(drawn)
    val currentOnTap by rememberUpdatedState(onSentenceTapped)
    val currentOnTapElsewhere by rememberUpdatedState(onTapElsewhere)

    val tappable = modifier.pointerInput(sentences) {
        detectTapGestures { position ->
            val offset = layout?.getOffsetForPosition(position)
            val sentence = offset
                ?.let { currentDrawn.sentenceAt(it) }
                ?.let { sentences.getOrNull(it) }

            // A tap between two sentences, or past the end of the last line, is
            // a tap on the page rather than on any words - and putting the
            // chrome away is what that has always meant.
            if (sentence == null) currentOnTapElsewhere() else currentOnTap(sentence, paragraph)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Crossfade(targetState = drawn, modifier = tappable) { paragraphText ->
            Text(
                text = paragraphText.text,
                style = style,
                // Both copies are on screen while the fade runs. Only the one
                // being faded in is what a tap should be measured against;
                // letting the outgoing copy report its layout would map taps
                // onto the paragraph that is on its way out.
                onTextLayout = { if (paragraphText === currentDrawn) layout = it }
            )
        }

        TranslatorsRemarks(sentences.map { translationOf(it.index) }, style)
    }
}

/**
 * What the translator had to say about this paragraph, under it.
 *
 * Two different things share this line, because they belong in the same place:
 * a remark about an ambiguous word, and the reason a sentence could not be
 * translated at all. Under rather than beside, because a note set into the prose
 * is a footnote in the middle of a sentence and the reader is here to read the
 * book.
 *
 * A failure has to appear somewhere. Marked only by a tint it is
 * indistinguishable from a sentence still being translated, which leaves
 * somebody watching a highlight and wondering whether anything is happening.
 */
@Composable
private fun TranslatorsRemarks(states: List<PassageTranslation?>, style: TextStyle) {
    val remarks = states.mapNotNull { state ->
        when (state) {
            null, PassageTranslation.Loading -> null

            is PassageTranslation.Failed -> Remark(state.error.displayShortMessage(), failed = true)

            is PassageTranslation.Ready -> state
                .takeIf { !it.showingOriginal }
                ?.translation
                ?.note
                ?.let { Remark(it, failed = false) }
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

private data class Remark(val text: String, val failed: Boolean)

/**
 * How each translated sentence is marked, in the reading colours.
 *
 * Every state the reader can see gets its own treatment, because they mean
 * different things: waiting is the original text tinted so somebody knows their
 * tap landed, a translation is the tint plus the new words, and a failure is
 * marked in the error colour so it reads as something to tap again rather than
 * as a translation that came out strangely.
 */
@Composable
private fun rememberSentenceMarks(states: List<PassageTranslation?>): Map<Int, SentenceMark> {
    val colors = MaterialTheme.colorScheme
    val pending = colors.onSurface.copy(alpha = PENDING_TINT)
    val translated = colors.primary.copy(alpha = TRANSLATED_TINT)
    val failed = colors.error.copy(alpha = FAILED_TINT)

    return remember(states, pending, translated, failed) {
        buildMap {
            states.forEachIndexed { index, state ->
                val mark = when (state) {
                    null -> null
                    PassageTranslation.Loading -> SentenceMark(null, SpanStyle(background = pending))
                    is PassageTranslation.Failed -> SentenceMark(null, SpanStyle(background = failed))
                    is PassageTranslation.Ready -> SentenceMark(
                        text = if (state.showingOriginal) null else state.translation.text,
                        style = SpanStyle(background = translated)
                    )
                }

                if (mark != null) put(index, mark)
            }
        }
    }
}

@Composable
private fun HeadingBlock(
    block: TextBlock.Heading,
    style: TextStyle,
    translationOf: (sentenceIndex: Int) -> PassageTranslation?,
    onSentenceTapped: (Sentence, paragraph: String) -> Unit,
    onTapElsewhere: () -> Unit
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
        translationOf = translationOf,
        onSentenceTapped = onSentenceTapped,
        onTapElsewhere = onTapElsewhere,
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
    translationOf: (sentenceIndex: Int) -> PassageTranslation?,
    onSentenceTapped: (Sentence, paragraph: String) -> Unit,
    onTapElsewhere: () -> Unit
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
            translationOf = translationOf,
            onSentenceTapped = onSentenceTapped,
            onTapElsewhere = onTapElsewhere,
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
    translationOf: (sentenceIndex: Int) -> PassageTranslation?,
    onSentenceTapped: (Sentence, paragraph: String) -> Unit,
    onTapElsewhere: () -> Unit
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
            translationOf = translationOf,
            onSentenceTapped = onSentenceTapped,
            onTapElsewhere = onTapElsewhere,
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
 * How faint each mark is. A sentence being worked on has to be findable again
 * without the page turning into a highlighter drawing - the reader is still
 * reading the text around it.
 */
private const val PENDING_TINT = 0.10f
private const val TRANSLATED_TINT = 0.14f
private const val FAILED_TINT = 0.16f

/** A note is an aside about the text, and is set smaller than the text. */
private const val NOTE_SCALE = 0.82f
