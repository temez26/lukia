package io.lukia.features.reader.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.reader.domain.TextBlock
import io.lukia.features.reader.presentation.TranslateOnScreen
import io.lukia.features.translate.domain.TranslationGestures
import io.lukia.features.translate.presentation.SentenceTranslations
import io.lukia.shared.presentation.components.LukiaDivider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The space between one block and the next, as a multiple of the text size. */
private const val BLOCK_SPACING = 0.7f

/** What a quotation and a list item are set in from the edge of the text. */
private val IndentWidth: Dp = 20.dp

/** Room for the widest marker a list is likely to have, so the text lines up. */
private val MarkerWidth: Dp = 28.dp

// The blocks that are not prose: headings, list items, quotes, code, figures
// and the breaks between sections.

@Composable
internal fun HeadingBlock(
    block: TextBlock.Heading,
    style: TextStyle,
    translations: SentenceTranslations,
    actions: BlockActions,
    translateOnScreen: TranslateOnScreen,
    gestures: TranslationGestures,
    resizingType: Boolean,
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
            fontWeight = FontWeight.SemiBold,
        ),
        translations = translations,
        actions = actions,
        translateOnScreen = translateOnScreen,
        gestures = gestures,
        resizingType = resizingType,
        modifier = Modifier
            .fillMaxWidth()
            // Bound to what it introduces rather than floating between two
            // blocks, so a heading sits with the text below it.
            .padding(top = 8.dp)
            .semantics { heading() },
    )
}

@Composable
internal fun ListItemBlock(
    block: TextBlock.ListItem,
    style: TextStyle,
    translations: SentenceTranslations,
    actions: BlockActions,
    translateOnScreen: TranslateOnScreen,
    gestures: TranslationGestures,
    resizingType: Boolean,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = block.marker,
            style = style,
            modifier = Modifier
                .padding(start = IndentWidth)
                .width(MarkerWidth),
        )

        ProseText(
            runs = block.text,
            style = style,
            translations = translations,
            actions = actions,
            translateOnScreen = translateOnScreen,
            gestures = gestures,
            resizingType = resizingType,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * A quotation, marked by a rule down its start edge as well as by the indent.
 * The indent alone is not enough on a narrow screen, where a paragraph set in
 * twenty points from the edge just looks like a paragraph.
 */
@Composable
internal fun QuoteBlock(
    block: TextBlock.Quote,
    style: TextStyle,
    translations: SentenceTranslations,
    actions: BlockActions,
    translateOnScreen: TranslateOnScreen,
    gestures: TranslationGestures,
    resizingType: Boolean,
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
                .background(MaterialTheme.colorScheme.outlineVariant),
        )

        ProseText(
            runs = block.text,
            style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            translations = translations,
            actions = actions,
            translateOnScreen = translateOnScreen,
            gestures = gestures,
            resizingType = resizingType,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Preformatted text, which is the one thing in a book that must not be re-wrapped
 * - so it scrolls sideways instead, and the book behind it does not.
 */
@Composable
internal fun CodeBlock(block: TextBlock.Code, style: TextStyle) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = block.text,
            style = style.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = style.fontSize * 0.88f,
                lineHeight = style.fontSize * 1.3f,
            ),
            softWrap = false,
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
internal fun FigureBlock(block: TextBlock.Figure) {
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
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A scene break: a short rule rather than a line across the whole measure. */
@Composable
internal fun SeparatorBlock() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        LukiaDivider(modifier = Modifier.fillMaxWidth(0.3f))
    }
}
