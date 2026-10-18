package io.lukia.features.reader.presentation.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.reader.domain.text.TextBlock

/**
 * How each kind of block is set, and how much room it takes around its text.
 *
 * One place rather than two because the reader both draws these blocks and
 * measures them: a book is laid out into pages by measuring what each block will
 * come to before any of it is drawn, and a measurement taken against a different
 * heading size or a different indent than the one drawn is a page that overflows
 * the screen it was cut to fit.
 */

/** The space between one block and the next, as a multiple of the text size. */
internal const val BLOCK_SPACING = 0.7f

/** What a quotation and a list item are set in from the edge of the text. */
internal val IndentWidth: Dp = 20.dp

/** Room for the widest marker a list is likely to have, so the text lines up. */
internal val MarkerWidth: Dp = 28.dp

/** The rule drawn down the start edge of a quotation. */
internal val QuoteRuleWidth: Dp = 3.dp

/**
 * Bound to what it introduces rather than floating between two blocks, so a
 * heading sits with the text below it.
 */
internal val HeadingTopPadding: Dp = 8.dp

/**
 * A chapter opening, as a printed book sets one: well clear of what came before,
 * larger, and closed with a short rule before the text begins.
 */
internal val ChapterTopPadding: Dp = 36.dp

/** Between a chapter's label and its title, which belong together. */
private val ChapterLabelGap: Dp = 6.dp
internal val ChapterRuleGap: Dp = 14.dp
internal val ChapterRuleWidth: Dp = 40.dp
internal val ChapterRuleHeight: Dp = 2.dp

internal val CodePaddingHorizontal: Dp = 12.dp
internal val CodePaddingVertical: Dp = 10.dp

/** The air a scene break is given, above and below the rule itself. */
internal val SeparatorPadding: Dp = 12.dp

internal val SeparatorRuleHeight: Dp = 1.dp

/**
 * How much larger than the body a heading is set, by its level. Four steps
 * rather than six: past the third, a book's headings are its own numbering
 * scheme and not a change of voice worth another size.
 */
private val HeadingScales = listOf(1.6f, 1.35f, 1.18f)

private const val DEEP_HEADING_SCALE = 1.05f

/** A chapter's title, whatever level its tag gave it. */
private const val CHAPTER_SCALE = 1.75f

/** A chapter title is often two lines of display type, set closer than a heading. */
private const val CHAPTER_LEADING = 1.18f

/** A chapter's label - "CHAPTER 5" - is small and spaced out, under the body size. */
private const val LABEL_SCALE = 0.85f
private const val LABEL_LEADING = 1.3f
private const val LABEL_TRACKING = 0.12f

/** What a heading is set clear of above it, by the part it plays. */
internal fun headingTopPadding(role: TextBlock.ChapterRole): Dp = when (role) {
    TextBlock.ChapterRole.None -> HeadingTopPadding
    TextBlock.ChapterRole.Label, TextBlock.ChapterRole.Title -> ChapterTopPadding
    TextBlock.ChapterRole.TitleUnderLabel -> ChapterLabelGap
}

/**
 * Headings are set tighter than prose: the leading that makes a paragraph
 * readable makes a two-line title look like two titles.
 */
private const val HEADING_LEADING = 1.25f

/** Preformatted text is set a little smaller, so a wide listing fits the measure. */
private const val CODE_SCALE = 0.88f

private const val CODE_LEADING = 1.3f

/**
 * A heading, set as the body size scaled up rather than at a size of its own -
 * which is what makes the text size setting act on the whole page instead of
 * only on its paragraphs.
 */
internal fun headingStyle(style: TextStyle, heading: TextBlock.Heading): TextStyle {
    if (heading.chapter == TextBlock.ChapterRole.Label) {
        return style.copy(
            fontSize = style.fontSize * LABEL_SCALE,
            lineHeight = style.fontSize * LABEL_SCALE * LABEL_LEADING,
            letterSpacing = (style.fontSize * LABEL_TRACKING),
            fontWeight = FontWeight.SemiBold,
            textAlign = if (style.textAlign == TextAlign.Justify) TextAlign.Start else style.textAlign,
        )
    }

    val scale = if (heading.opensChapter) {
        CHAPTER_SCALE
    } else {
        HeadingScales.getOrElse(heading.level - 1) { DEEP_HEADING_SCALE }
    }
    val leading = if (heading.opensChapter) CHAPTER_LEADING else HEADING_LEADING

    return style.copy(
        fontSize = style.fontSize * scale,
        lineHeight = style.fontSize * scale * leading,
        fontWeight = if (heading.opensChapter) FontWeight.Bold else FontWeight.SemiBold,
        // Never justified, whatever the prose is set to: a title is a handful
        // of words on a line or two, and stretching them to both edges leaves
        // gaps a sentence's worth wide. Anything else it has been given stands,
        // so a book that centred its part titles keeps them centred.
        textAlign = if (style.textAlign == TextAlign.Justify) TextAlign.Start else style.textAlign,
    )
}

/** Preformatted text, which is the one thing in a book that must not be re-wrapped. */
internal fun codeStyle(style: TextStyle): TextStyle = style.copy(
    fontFamily = FontFamily.Monospace,
    fontSize = style.fontSize * CODE_SCALE,
    lineHeight = style.fontSize * CODE_LEADING,
    // Preformatted text is the one thing whose spacing is content: justifying it
    // would move the columns of a table and the indents of a program.
    textAlign = TextAlign.Start,
)
