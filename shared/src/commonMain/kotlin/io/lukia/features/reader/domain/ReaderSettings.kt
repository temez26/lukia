package io.lukia.features.reader.domain

import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt

/** The reading surface: what colour the page and everything around it takes. */
enum class ReaderTheme {
    Light,
    Sepia,
    Dark
}

/** Body face for reflowable text. Both resolve to the platform's own faces. */
enum class ReaderTypeface {
    SansSerif,
    Serif
}

/** Leading presets, as a multiple of the text size. */
enum class LineSpacing(val multiplier: Float) {
    Tight(1.2f),
    Normal(1.45f),
    Relaxed(1.75f)
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
    Largest(1.5f);

    /** True once the page no longer fits the space it is drawn in. */
    val overflowsColumn: Boolean
        get() = fraction > 1f
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
    val typeface: ReaderTypeface = ReaderTypeface.SansSerif
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
