package io.lukia.features.reader.presentation.pagination

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import io.lukia.features.reader.domain.TextBlock
import io.lukia.features.reader.domain.TextRun
import io.lukia.features.reader.presentation.components.spanStyle
import io.lukia.features.reader.presentation.theme.BLOCK_SPACING
import io.lukia.features.reader.presentation.theme.CodePaddingVertical
import io.lukia.features.reader.presentation.theme.HeadingTopPadding
import io.lukia.features.reader.presentation.theme.IndentWidth
import io.lukia.features.reader.presentation.theme.MarkerWidth
import io.lukia.features.reader.presentation.theme.QuoteRuleWidth
import io.lukia.features.reader.presentation.theme.SeparatorPadding
import io.lukia.features.reader.presentation.theme.SeparatorRuleHeight
import io.lukia.features.reader.presentation.theme.codeStyle
import io.lukia.features.reader.presentation.theme.headingStyle

/**
 * How tall each block of a book comes to, and where one has to be cut to fit.
 *
 * An interface because measuring text needs a window, a density and a font, and
 * dividing a book into pages needs none of those - it needs to know how tall
 * things are. Keeping the two apart is what lets the rule that fills a page be
 * checked against blocks of a known height rather than against a rendering.
 */
internal interface BlockSizes {

    /** The space between one block and the next. */
    val spacingPx: Int

    fun height(block: TextBlock): Int

    /**
     * The last character of [block] that still fits in [heightPx], for a block
     * too tall to go on a page whole. Zero when not even the first line fits,
     * which is the caller's signal that the page it offered is not one to put
     * text on.
     */
    fun fittingLength(block: TextBlock, heightPx: Int): Int
}

/**
 * How tall each block of a book comes to, at one width and one type size.
 *
 * The book is cut into pages by asking this rather than by drawing anything: a
 * page is only a page if what is on it was known to fit before it was composed,
 * and laying the whole book out in the window to find out where the screenfuls
 * end would be composing every chapter to show one of them.
 *
 * Every measurement is taken against the same styles and the same insets the
 * blocks are actually drawn with - see `BlockStyle` - so this is a rehearsal of
 * the layout rather than an estimate of it. Two things it cannot know about are
 * a figure it has not been given the shape of, and a translation that has not
 * arrived: the first is asked of [figureHeight], and the second moves the text
 * after it has been paginated, which is what the block height animation is for.
 */
internal class BlockMetrics(
    private val measurer: TextMeasurer,
    private val style: TextStyle,
    /** The measure the text is set to, in pixels. */
    private val widthPx: Int,
    density: Density,
    /** How tall an illustration comes out at [widthPx], or zero for one that cannot be shown. */
    private val figureHeight: (TextBlock.Figure, widthPx: Int) -> Int,
) : BlockSizes {

    private val indentPx = with(density) { IndentWidth.roundToPx() }
    private val markerPx = with(density) { MarkerWidth.roundToPx() }
    private val quoteRulePx = with(density) { QuoteRuleWidth.roundToPx() }
    private val headingTopPx = with(density) { HeadingTopPadding.roundToPx() }
    private val codeVerticalPx = with(density) { CodePaddingVertical.roundToPx() }
    private val separatorPx = with(density) {
        SeparatorPadding.roundToPx() * 2 + SeparatorRuleHeight.roundToPx()
    }

    override val spacingPx: Int = with(density) { (style.fontSize * BLOCK_SPACING).toPx().toInt() }

    override fun height(block: TextBlock): Int = when (block) {
        is TextBlock.Heading -> headingTopPx + layout(block).size.height
        is TextBlock.Paragraph -> layout(block).size.height
        is TextBlock.ListItem -> layout(block).size.height
        is TextBlock.Quote -> layout(block).size.height
        is TextBlock.Code -> codeVerticalPx * 2 + layout(block).size.height
        is TextBlock.Figure -> figureHeight(block, widthPx)
        TextBlock.Separator -> separatorPx
    }

    /**
     * Cut at a line rather than at a word or a sentence: a line is where the
     * text already breaks, so a page ending on one ends where the reader's eye
     * was going anyway.
     */
    override fun fittingLength(block: TextBlock, heightPx: Int): Int {
        val layout = layout(block)
        val text = blockText(block)

        if (layout.size.height <= heightPx) return text.length

        val available = heightPx - verticalInset(block)
        var fitting = 0

        for (line in 0 until layout.lineCount) {
            if (layout.getLineBottom(line) > available) break
            fitting = layout.getLineEnd(line, visibleEnd = false)
        }

        return fitting.coerceAtMost(text.length)
    }

    /** The room a block takes over and above its text: its own padding. */
    private fun verticalInset(block: TextBlock): Int = when (block) {
        is TextBlock.Heading -> headingTopPx
        is TextBlock.Code -> codeVerticalPx * 2
        else -> 0
    }

    private fun layout(block: TextBlock) = when (block) {
        is TextBlock.Heading -> measure(
            block.text.annotated(),
            headingStyle(style, block.level),
            widthPx,
        )

        is TextBlock.Paragraph -> measure(block.text.annotated(), style, widthPx)

        is TextBlock.ListItem -> measure(
            block.text.annotated(),
            style,
            widthPx - indentPx - markerPx,
        )

        is TextBlock.Quote -> measure(
            block.text.annotated(),
            style,
            widthPx - indentPx - quoteRulePx,
        )

        // Preformatted text is not wrapped, so its height is its own line count
        // however far off the side of the page the longest of them runs.
        is TextBlock.Code -> measure(
            AnnotatedString(block.text),
            codeStyle(style),
            width = Int.MAX_VALUE,
            softWrap = false,
        )

        else -> measure(AnnotatedString(""), style, widthPx)
    }

    private fun measure(
        text: AnnotatedString,
        style: TextStyle,
        width: Int,
        softWrap: Boolean = true,
    ) = measurer.measure(
        text = text,
        style = style,
        softWrap = softWrap,
        constraints = Constraints(maxWidth = width.coerceAtLeast(1)),
    )
}

/** The characters a block holds, which is what a cut into it is an offset into. */
internal fun blockText(block: TextBlock): String = when (block) {
    is TextBlock.Heading -> block.text.plain()
    is TextBlock.Paragraph -> block.text.plain()
    is TextBlock.ListItem -> block.text.plain()
    is TextBlock.Quote -> block.text.plain()
    is TextBlock.Code -> block.text
    else -> ""
}

private fun List<TextRun>.plain(): String = joinToString(separator = "") { it.text }

/** The runs as they are set on the page, marks and all - which is what is measured. */
private fun List<TextRun>.annotated(): AnnotatedString = buildAnnotatedString {
    this@annotated.forEach { run ->
        withStyle(run.emphasis.spanStyle()) { append(run.text) }
    }
}
