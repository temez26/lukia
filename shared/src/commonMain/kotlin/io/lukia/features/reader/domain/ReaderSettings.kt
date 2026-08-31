package io.lukia.features.reader.domain

import io.lukia.core.domain.reading.PdfLayout
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt

/** The reading surface: what colour the page and everything around it takes. */
enum class ReaderTheme {
    Light,
    Sepia,
    Dark,
}

/** Body face for reflowable text. Both resolve to the platform's own faces. */
enum class ReaderTypeface {
    SansSerif,
    Serif,
}

/**
 * How the lines of a paragraph meet its edges.
 *
 * [Justified] stretches the spaces in every line but the last so both edges of
 * the column are straight, which is how a printed book is set. [Ragged] leaves
 * the spaces even and the far edge uneven, which is easier to read on a narrow
 * column - justifying forty characters opens rivers of white space down the
 * page, and there is no hyphenation here to close them.
 *
 * Both follow the direction the text is written in rather than picking a side,
 * so a book set right-to-left is ragged on its left.
 */
enum class TextAlignment {
    Ragged,
    Justified,
}

/** Leading presets, as a multiple of the text size. */
enum class LineSpacing(val multiplier: Float) {
    Tight(1.2f),
    Normal(1.45f),
    Relaxed(1.75f),
}

/**
 * The page widths the settings panel offers as one tap.
 *
 * Presets rather than the setting itself: the page scale is continuous, because
 * pinching has to be able to land anywhere between these. These are the places
 * worth having a shortcut to.
 */
enum class ReadingWidth(val fraction: Float) {
    Narrow(0.75f),
    Medium(0.9f),
    Wide(1f),
    Larger(1.25f),
    Largest(1.5f),
    ;

    /** True once the page no longer fits the space it is drawn in. */
    val overflowsColumn: Boolean
        get() = fraction > 1f
}

private const val HALF_ITS_SIZE = 0.5f
private const val ITS_OWN_SIZE = 1f
private const val HALF_AS_LARGE_AGAIN = 1.5f

/**
 * How large an illustration may be drawn, against its own size.
 *
 * A reflowable book states no size for its pictures, so the reader draws each at
 * the size it actually is and shrinks it where there is not room - which is what
 * a browser does, and what the books were laid out against. That is right for
 * most books and wrong for two kinds: one whose diagrams were saved small and
 * are now hard to read, and one whose plates are meant to fill the page.
 *
 * [Fill] is the second of those - every illustration drawn to the measure,
 * whatever its own size. It is not a scale at all, which is why it is a preset
 * here rather than a number the reader types.
 */
enum class FigureSize(val scale: Float) {
    Small(HALF_ITS_SIZE),
    Normal(ITS_OWN_SIZE),
    Large(HALF_AS_LARGE_AGAIN),
    Fill(Float.POSITIVE_INFINITY),
    ;

    /** Whether an illustration is drawn to the measure however small it is. */
    val fillsMeasure: Boolean get() = scale.isInfinite()
}

/**
 * Everything about how the reader presents a book, as opposed to what the book
 * says. One value, carried whole, so adding a setting later is a field here and
 * a key in the repository rather than a new pipe through the reader.
 *
 * The defaults are the comfortable configuration: paper-white pages at the full
 * width the window allows, which is what the reader looked like before any of
 * this was settable.
 */
data class ReaderSettings(
    val theme: ReaderTheme = ReaderTheme.Light,
    val textScale: Float = DEFAULT_TEXT_SCALE,
    val lineSpacing: LineSpacing = LineSpacing.Normal,
    /**
     * How wide the page is drawn, against the column the window allows. Above
     * 1 the page is deliberately wider than the space available, which is how a
     * reader gets closer to small print - the page is panned sideways rather
     * than shrunk to fit. A PDF's type is a fixed size, so this is the only way
     * to make it bigger.
     *
     * Continuous rather than one of [ReadingWidth]'s presets, because pinching
     * has to be able to settle anywhere.
     */
    val pageScale: Float = DEFAULT_PAGE_SCALE,
    val typeface: ReaderTypeface = ReaderTypeface.SansSerif,
    /**
     * How paragraphs meet the edge of the column. Reflowable books only: a PDF's
     * lines were broken and spaced when the file was made.
     */
    val alignment: TextAlignment = TextAlignment.Ragged,
    /**
     * How large illustrations are drawn. Reflowable books only: a PDF's pictures
     * were placed on its pages when the file was made, and the reader has no
     * more say over one of them than over where a line breaks.
     */
    val figureSize: FigureSize = FigureSize.Normal,
    /**
     * Whether the device is asked not to dim and lock while a book is open.
     *
     * On by default, and a mobile setting only: a page somebody is reading is a
     * page nobody is touching, and a phone that locks itself mid-paragraph is
     * the oldest complaint there is about reading on one. It costs battery,
     * which is why it is a switch rather than something the reader is simply
     * given - and why it stops the moment the book is closed rather than for as
     * long as the app is running.
     */
    val keepScreenAwake: Boolean = true,
    /**
     * What a PDF nobody has chosen for opens as.
     *
     * A starting point rather than a rule: how a book is read stays that book's
     * own choice, and one that has been opened either way keeps what it was
     * given whatever this says afterwards. It is here for the reader whose shelf
     * is all PDFs and who re-sets every one of them - choosing that once is
     * better than choosing it once a book.
     */
    val defaultPdfLayout: PdfLayout = PdfLayout.Original,
) {

    /** The preset this scale sits on, if it sits on one. */
    val matchingWidth: ReadingWidth?
        get() = ReadingWidth.entries.firstOrNull {
            abs(it.fraction - pageScale) < PRESET_TOLERANCE
        }

    /**
     * Clamped here rather than at the gesture, so no pinch can shrink the page
     * to a thumbnail or blow it up past what is useful to pan around.
     */
    fun withPageScale(scale: Float): ReaderSettings {
        val clamped = scale.coerceIn(MIN_PAGE_SCALE, MAX_PAGE_SCALE)
        return copy(pageScale = round(clamped * 100f) / 100f)
    }

    val canEnlargeText: Boolean
        get() = textScale < MAX_TEXT_SCALE

    val canReduceText: Boolean
        get() = textScale > MIN_TEXT_SCALE

    /** For the label beside the stepper - "100%" reads better than "1.0". */
    val textScalePercent: Int
        get() = (textScale * 100).roundToInt()

    fun withLargerText(): ReaderSettings = withTextScale(textScale + TEXT_SCALE_STEP)

    fun withSmallerText(): ReaderSettings = withTextScale(textScale - TEXT_SCALE_STEP)

    /**
     * Clamped here rather than at the buttons, so no caller can produce a size
     * that is unreadably small or so large that a line holds three words.
     *
     * Rounded to the step as well as clamped: stepping by a fraction repeatedly
     * otherwise drifts into values like 1.3000001, which then persist and come
     * back looking like corruption.
     */
    fun withTextScale(scale: Float): ReaderSettings {
        val clamped = scale.coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE)
        return copy(textScale = round(clamped * 100f) / 100f)
    }

    companion object {
        const val MIN_TEXT_SCALE = 0.8f
        const val MAX_TEXT_SCALE = 2.0f
        const val TEXT_SCALE_STEP = 0.1f
        const val DEFAULT_TEXT_SCALE = 1.0f

        const val MIN_PAGE_SCALE = 0.75f
        const val MAX_PAGE_SCALE = 3.0f
        const val DEFAULT_PAGE_SCALE = 1.0f

        /** How close to a preset counts as being on it. */
        private const val PRESET_TOLERANCE = 0.005f
    }
}
