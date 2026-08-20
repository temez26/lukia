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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.reader.domain.TextBlock
import io.lukia.features.reader.presentation.TranslateOnScreen
import io.lukia.features.reader.presentation.theme.CodePaddingHorizontal
import io.lukia.features.reader.presentation.theme.CodePaddingVertical
import io.lukia.features.reader.presentation.theme.HeadingTopPadding
import io.lukia.features.reader.presentation.theme.IndentWidth
import io.lukia.features.reader.presentation.theme.MarkerWidth
import io.lukia.features.reader.presentation.theme.QuoteRuleWidth
import io.lukia.features.reader.presentation.theme.SeparatorPadding
import io.lukia.features.reader.presentation.theme.codeStyle
import io.lukia.features.reader.presentation.theme.headingStyle
import io.lukia.features.translate.domain.TranslationGestures
import io.lukia.features.translate.presentation.SentenceTranslations
import io.lukia.shared.presentation.components.LukiaDivider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// The blocks that are not prose: headings, list items, quotes, code, figures
// and the breaks between scenes.

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
    ProseText(
        runs = block.text,
        style = headingStyle(style, block.level),
        translations = translations,
        actions = actions,
        translateOnScreen = translateOnScreen,
        gestures = gestures,
        resizingType = resizingType,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = HeadingTopPadding)
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
                .width(QuoteRuleWidth)
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
            .padding(
                horizontal = CodePaddingHorizontal,
                vertical = CodePaddingVertical,
            ),
    ) {
        Text(
            text = block.text,
            style = codeStyle(style),
            softWrap = false,
        )
    }
}

/**
 * An illustration, decoded only once it is on screen.
 *
 * A book can carry hundreds of these, and decoding them all when it opens would
 * cost more memory than every word in it. Held as bytes until here, and dropped
 * again by Compose when the page scrolls far enough away to be discarded.
 *
 * A figure that cannot be decoded draws nothing rather than a broken-image mark:
 * the book is still perfectly readable without it, and a row of error icons down
 * a chapter is worse than a chapter with a gap.
 *
 * [maxHeight] is how tall it may be drawn, which on a paginated book is the page
 * it was put on. A plate taller than the page is scaled down to fit rather than
 * running off the bottom of it, so an illustration is always seen whole.
 */
@Composable
internal fun FigureBlock(block: TextBlock.Figure, maxHeight: Dp = Dp.Unspecified) {
    val image by produceState<ImageBitmap?>(initialValue = null, block) {
        value = withContext(Dispatchers.Default) {
            runCatching { block.bytes.decodeToImageBitmap() }.getOrNull()
        }
    }

    image?.let { bitmap ->
        Image(
            bitmap = bitmap,
            contentDescription = block.description?.takeIf { it.isNotBlank() },
            // Fit rather than FillWidth wherever the height is bounded: an image
            // wider than it is tall must give way on both measures at once.
            contentScale = if (maxHeight == Dp.Unspecified) {
                ContentScale.FillWidth
            } else {
                ContentScale.Fit
            },
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (maxHeight == Dp.Unspecified) {
                        Modifier
                    } else {
                        Modifier.heightIn(max = maxHeight)
                    },
                ),
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
            .padding(vertical = SeparatorPadding),
    ) {
        LukiaDivider(modifier = Modifier.fillMaxWidth(0.3f))
    }
}
