package io.lukia.shared.presentation.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Window width buckets, following the Material/Apple convention of three
 * breakpoints. Phones are Compact, tablets and small desktop windows are Medium,
 * and maximised desktop windows are Expanded.
 */
enum class WindowWidthClass {
    Compact,
    Medium,
    Expanded;

    companion object {
        fun fromWidth(width: Dp): WindowWidthClass = when {
            width < 600.dp -> Compact
            width < 840.dp -> Medium
            else -> Expanded
        }
    }
}

/**
 * Apple's minimum comfortable target, and the size every control in the app
 * settles on - toolbar buttons, menu rows, segmented tracks, chapter rows.
 *
 * Deliberately not part of [LukiaDimens]: those metrics scale with the window,
 * and this one must not. A finger is the same size on a phone as on a tablet, so
 * a target that grew with the window would be answering the wrong question.
 */
val MinTouchTarget: Dp = 44.dp

/**
 * Layout metrics that scale with the window. Keeping these in one place is what
 * stops the desktop build from simply stretching the phone layout edge to edge.
 */
data class LukiaDimens(
    /** Horizontal inset from the window edge to content. */
    val screenPadding: Dp,
    /** Minimum width of one book cell; the grid fits as many as will fit. */
    val bookCellMinWidth: Dp,
    /** Gap between book cells, horizontally and vertically. */
    val gridSpacing: Dp,
    /**
     * Cap on how wide the grid may grow. Without this, an ultrawide monitor
     * produces a single row of very long scan lines that is tiring to read.
     */
    val maxGridWidth: Dp,
    /** Cap on the reading-detail column, kept near a comfortable measure. */
    val maxDetailWidth: Dp,
    /**
     * Cap on how wide a page is drawn in the reader. [Dp.Unspecified] lets the
     * page fill the window, which is what a phone wants - every millimetre of a
     * small screen counts. Wider windows cap instead, since a page stretched
     * across a monitor is both unreadable and expensive to render.
     */
    val maxReaderWidth: Dp,
    /** Width of the cover art on the detail screen. */
    val detailCoverWidth: Dp,
    val sectionSpacing: Dp
)

private val CompactDimens = LukiaDimens(
    screenPadding = 16.dp,
    // Sized so a 390dp phone fits three columns rather than two, which is the
    // density every mainstream reading app settles on.
    bookCellMinWidth = 104.dp,
    gridSpacing = 16.dp,
    maxGridWidth = Dp.Unspecified,
    maxDetailWidth = 480.dp,
    maxReaderWidth = Dp.Unspecified,
    detailCoverWidth = 200.dp,
    sectionSpacing = 24.dp
)

private val MediumDimens = LukiaDimens(
    screenPadding = 24.dp,
    bookCellMinWidth = 140.dp,
    gridSpacing = 20.dp,
    maxGridWidth = 900.dp,
    maxDetailWidth = 520.dp,
    maxReaderWidth = 720.dp,
    detailCoverWidth = 220.dp,
    sectionSpacing = 28.dp
)

private val ExpandedDimens = LukiaDimens(
    screenPadding = 32.dp,
    bookCellMinWidth = 160.dp,
    gridSpacing = 24.dp,
    maxGridWidth = 1160.dp,
    maxDetailWidth = 560.dp,
    maxReaderWidth = 860.dp,
    detailCoverWidth = 240.dp,
    sectionSpacing = 32.dp
)

internal fun dimensFor(widthClass: WindowWidthClass): LukiaDimens = when (widthClass) {
    WindowWidthClass.Compact -> CompactDimens
    WindowWidthClass.Medium -> MediumDimens
    WindowWidthClass.Expanded -> ExpandedDimens
}

val LocalLukiaDimens = staticCompositionLocalOf { CompactDimens }

val LocalWindowWidthClass = staticCompositionLocalOf { WindowWidthClass.Compact }
