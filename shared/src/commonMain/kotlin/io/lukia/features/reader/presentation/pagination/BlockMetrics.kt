package io.lukia.features.reader.presentation.pagination

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import io.lukia.features.reader.domain.text.TextBlock
import io.lukia.features.reader.domain.text.TextRun
import io.lukia.features.reader.presentation.components.text.spanStyle
import io.lukia.features.reader.presentation.theme.BLOCK_SPACING
import io.lukia.features.reader.presentation.theme.ChapterRuleGap
import io.lukia.features.reader.presentation.theme.ChapterRuleHeight
import io.lukia.features.reader.presentation.theme.CodePaddingVertical
import io.lukia.features.reader.presentation.theme.IndentWidth
import io.lukia.features.reader.presentation.theme.MarkerWidth
import io.lukia.features.reader.presentation.theme.QuoteRuleWidth
import io.lukia.features.reader.presentation.theme.SeparatorPadding
import io.lukia.features.reader.presentation.theme.SeparatorRuleHeight
import io.lukia.features.reader.presentation.theme.codeStyle
import io.lukia.features.reader.presentation.theme.headingStyle
import io.lukia.features.reader.presentation.theme.headingTopPadding

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
 * The book is cut into pages by asking this rather than by drawing: a page is
 * only a page if what is on it was known to fit before it was composed, and
 * laying the whole book out to find where the screenfuls end would compose every
 * chapter to show one.
 *
 * Measured against the same styles and insets the blocks are drawn with - see
 * `BlockStyle` - so this is a rehearsal of the layout rather than an estimate.
 * Two things it cannot know: a figure whose shape it was not given, asked of
 * [figureHeight]; and a translation that has not arrived, which moves the text
 * after pagination and is what the block height animation is for.
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
    private val headingTopPx = TextBlock.ChapterRole.entries.associateWith { role ->
        with(density) { headingTopPadding(role).roundToPx() }
    }
    private val chapterRulePx = with(density) { ChapterRuleGap.roundToPx() + ChapterRuleHeight.roundToPx() }
    private val codeVerticalPx = with(density) { CodePaddingVertical.roundToPx() }
    private val separatorPx = with(density) {
        SeparatorPadding.roundToPx() * 2 + SeparatorRuleHeight.roundToPx()
    }

    override val spacingPx: Int = with(density) { (style.fontSize * BLOCK_SPACING).toPx().toInt() }

    override fun height(block: TextBlock): Int = when (block) {
        is TextBlock.Heading -> verticalInset(block) + layout(block).size.height
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
        is TextBlock.Heading -> headingTopPx.getValue(block.chapter) + if (block.opensChapter) chapterRulePx else 0
        is TextBlock.Code -> codeVerticalPx * 2
        else -> 0
    }

    /**
     * The last block laid out, and what came of laying it out.
     *
     * `PageBuilder` measures each block once and carries the number through the
     * questions it asks - so what is left for this to catch is the one block
     * that is laid out twice for two different answers: a block too tall for the
     * page, whose height says it must be cut and whose [fittingLength] then
     * needs the same layout again to find the line to cut at.
     *
     * What that saves is not the measuring - [TextMeasurer] keeps a cache of its
     * own and the repeat hits it. It is the cost of asking: an
     * [AnnotatedString] rebuilt from the block's runs, and a probe that compares
     * a whole paragraph character by character to find the entry already sitting
     * there.
     *
     * Held by identity rather than by value, because comparing two blocks means
     * comparing their text, which is the comparison this exists to avoid. A
     * block is one object handed down from the parse, so identity is what the
     * two calls actually share - and the halves a cut produces are new objects,
     * which miss and are measured as they should be.
     *
     * Plain `var`s because one of these belongs to one worker and is asked only
     * from it: the runs are dispatched under a lock per measurer, so no two
     * coroutines are ever inside the same [BlockMetrics]. See
     * `paginateProgressively`.
     */
    private var lastBlock: TextBlock? = null
    private var lastLayout: TextLayoutResult? = null

    private fun layout(block: TextBlock): TextLayoutResult {
        lastLayout?.takeIf { lastBlock === block }?.let { return it }

        return laidOut(block).also {
            lastBlock = block
            lastLayout = it
        }
    }

    private fun laidOut(block: TextBlock): TextLayoutResult = when (block) {
        is TextBlock.Heading -> measure(
            block.text.annotated(),
            headingStyle(style, block),
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
