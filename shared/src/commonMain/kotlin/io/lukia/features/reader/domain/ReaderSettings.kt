package io.lukia.features.reader.domain

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
 * How much of the available column the content may take. A fraction rather than
 * a width: the window already caps itself per size class through
 * `LukiaDimens.maxReaderWidth`, and this narrows the measure inside that.
 */
enum class ReadingWidth(val fraction: Float) {
    Narrow(0.72f),
    Medium(0.86f),
    Wide(1f)
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
    val readingWidth: ReadingWidth = ReadingWidth.Wide,
    val typeface: ReaderTypeface = ReaderTypeface.SansSerif
) {

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
    }
}
