package io.lukia.features.reader.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.reader.domain.Emphasis
import io.lukia.features.reader.domain.TextBlock
import io.lukia.features.reader.domain.TextRun
import io.lukia.features.reader.domain.TextSection
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
 */
@Composable
internal fun TextSectionView(
    section: TextSection,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    val spacing = with(LocalDensity.current) {
        (style.fontSize * BLOCK_SPACING).toDp()
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(spacing),
        modifier = modifier
    ) {
        section.blocks.forEach { block ->
            TextBlockView(block = block, style = style)
        }
    }
}

@Composable
private fun TextBlockView(block: TextBlock, style: TextStyle) {
    when (block) {
        is TextBlock.Heading -> HeadingBlock(block, style)
        is TextBlock.Paragraph -> Text(text = block.text.annotated(), style = style)
        is TextBlock.ListItem -> ListItemBlock(block, style)
        is TextBlock.Quote -> QuoteBlock(block, style)
        is TextBlock.Code -> CodeBlock(block, style)
        is TextBlock.Figure -> FigureBlock(block)
        TextBlock.Separator -> SeparatorBlock()
    }
}

/**
 * Headings are scaled off the body size, so a chapter title is always the same
 * amount larger than the text under it however large that text has been set.
 *
 * Levels below the third are given no size of their own - past that point a
 * document's heading levels are its own numbering scheme, and three visibly
 * different sizes is as many as a reader can tell apart anyway.
 */
@Composable
private fun HeadingBlock(block: TextBlock.Heading, style: TextStyle) {
    val scale = when (block.level) {
        1 -> 1.6f
        2 -> 1.35f
        3 -> 1.18f
        else -> 1.05f
    }

    Text(
        text = block.text.annotated(),
        style = style.copy(
            fontSize = style.fontSize * scale,
            // Headings are set tighter than prose: the leading that makes a
            // paragraph readable makes a two-line title look like two titles.
            lineHeight = style.fontSize * scale * 1.25f,
            fontWeight = FontWeight.SemiBold
        ),
        modifier = Modifier
            .fillMaxWidth()
            // Bound to what it introduces rather than floating between two
            // blocks, so a heading sits with the text below it.
            .padding(top = 8.dp)
            .semantics { heading() }
    )
}

@Composable
private fun ListItemBlock(block: TextBlock.ListItem, style: TextStyle) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = block.marker,
            style = style,
            modifier = Modifier
                .padding(start = IndentWidth)
                .width(MarkerWidth)
        )

        Text(text = block.text.annotated(), style = style, modifier = Modifier.weight(1f))
    }
}

/**
 * A quotation, marked by a rule down its start edge as well as by the indent.
 * The indent alone is not enough on a narrow screen, where a paragraph set in
 * twenty points from the edge just looks like a paragraph.
 */
@Composable
private fun QuoteBlock(block: TextBlock.Quote, style: TextStyle) {
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

        Text(
            text = block.text.annotated(),
            style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
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
            .clip(RoundedCornerShape(8.dp))
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
        HorizontalDivider(
            thickness = Dp.Hairline,
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.fillMaxWidth(0.3f)
        )
    }
}

/**
 * The runs as one string with their marks applied.
 *
 * Remembered against the runs themselves: this is rebuilt on every recomposition
 * otherwise, and a chapter is hundreds of these.
 */
@Composable
private fun List<TextRun>.annotated(): AnnotatedString = remember(this) {
    buildAnnotatedString {
        this@annotated.forEach { run ->
            if (run.emphasis.isEmpty()) {
                append(run.text)
                return@forEach
            }

            withStyle(run.emphasis.spanStyle()) { append(run.text) }
        }
    }
}

private fun Set<Emphasis>.spanStyle(): SpanStyle = SpanStyle(
    fontWeight = if (Emphasis.Bold in this) FontWeight.Bold else null,
    fontStyle = if (Emphasis.Italic in this) FontStyle.Italic else null,
    fontFamily = if (Emphasis.Monospace in this) FontFamily.Monospace else null
)
