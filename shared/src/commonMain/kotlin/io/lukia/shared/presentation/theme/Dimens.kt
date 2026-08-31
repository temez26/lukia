package io.lukia.shared.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Window width buckets.
 *
 * The first three follow the Material/Apple convention: phones are Compact,
 * tablets and small desktop windows are Medium, and a comfortably sized desktop
 * window is Expanded.
 *
 * [Large] exists because three buckets stop being enough on a desktop. Without
 * it a 900dp window and a maximised 2560dp monitor were handed identical
 * metrics, so the monitor spent most of its width on empty margin either side of
 * a grid sized for the smaller of the two.
 */
enum class WindowWidthClass {
    Compact,
    Medium,
    Expanded,
    Large,
    ;

    companion object {
        fun fromWidth(width: Dp): WindowWidthClass = when {
            width < 600.dp -> Compact
            width < 840.dp -> Medium
            width < 1280.dp -> Expanded
            else -> Large
        }
    }
}

/**
 * Window height buckets, on Material's own breakpoints.
 *
 * Width alone cannot answer every layout question, and the one it kept getting
 * wrong is the phone turned on its side. A 390x844 phone in landscape is 844dp
 * wide - Medium, which is what a small tablet reports - and 390dp tall, which
 * nothing else in the app is. Everything sized against the width therefore grew,
 * and the vertical room it grew into was not there.
 *
 * [Compact] is the bucket that matters: it is a phone in landscape, and the
 * split-screen half of a tablet. Chrome that costs a fixed number of points has
 * to cost fewer of them here, because the points are what has run out.
 */
enum class WindowHeightClass {
    Compact,
    Medium,
    Expanded,
    ;

    companion object {
        fun fromHeight(height: Dp): WindowHeightClass = when {
            height < 480.dp -> Compact
            height < 900.dp -> Medium
            else -> Expanded
        }
    }
}

/**
 * Apple's minimum comfortable target, and what every control settles on where a
 * finger is what does the pointing.
 *
 * Deliberately not part of [LukiaDimens]: those metrics scale with the window,
 * and this one must not. See [LukiaMetrics], which is what actually decides a
 * control's size and resolves to this on Compact windows.
 */
val MinTouchTarget: Dp = 44.dp

/**
 * Layout metrics that scale with the window. Keeping these in one place is what
 * stops the desktop build from simply stretching the phone layout edge to edge.
 */
@Immutable
data class LukiaDimens(
    /** Horizontal inset from the window edge to content. */
    val screenPadding: Dp,
    /** Minimum width of one book cell; the grid fits as many as will fit. */
    val bookCellMinWidth: Dp,
    /** Gap between book cells across a row. */
    val gridSpacing: Dp,
    /**
     * Gap between grid rows. Separate from [gridSpacing] because a cell carries a
     * caption under its cover, so the visible gap down the page already starts
     * larger than the gap across it; matching the two makes the rows read as
     * further apart than the columns.
     */
    val gridRowSpacing: Dp,
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
    /** Gap between one block of content and the next within a screen. */
    val sectionSpacing: Dp,
    /** Inset from a floating panel's edge to its contents. */
    val panelPadding: Dp,
)

private val CompactDimens = LukiaDimens(
    screenPadding = 16.dp,
    // Sized so a 390dp phone fits three columns rather than two, which is the
    // density every mainstream reading app settles on.
    bookCellMinWidth = 104.dp,
    gridSpacing = 14.dp,
    gridRowSpacing = 20.dp,
    maxGridWidth = Dp.Unspecified,
    maxDetailWidth = 480.dp,
    maxReaderWidth = Dp.Unspecified,
    detailCoverWidth = 200.dp,
    sectionSpacing = 22.dp,
    panelPadding = 16.dp,
)

private val MediumDimens = LukiaDimens(
    screenPadding = 20.dp,
    bookCellMinWidth = 132.dp,
    gridSpacing = 16.dp,
    gridRowSpacing = 22.dp,
    maxGridWidth = 900.dp,
    maxDetailWidth = 520.dp,
    maxReaderWidth = 720.dp,
    detailCoverWidth = 200.dp,
    sectionSpacing = 22.dp,
    panelPadding = 14.dp,
)

private val ExpandedDimens = LukiaDimens(
    screenPadding = 24.dp,
    bookCellMinWidth = 142.dp,
    gridSpacing = 18.dp,
    gridRowSpacing = 24.dp,
    maxGridWidth = 1280.dp,
    maxDetailWidth = 560.dp,
    maxReaderWidth = 800.dp,
    detailCoverWidth = 208.dp,
    sectionSpacing = 24.dp,
    panelPadding = 14.dp,
)

/**
 * A maximised desktop window. The grid is allowed most of the width rather than
 * a phone-sized column in the middle of it, and the cells grow a little so the
 * extra room becomes larger covers as well as more of them.
 *
 * The reading column does not follow. A measure is a measure whatever the
 * monitor is capable of, and a line of prose two feet long is unreadable no
 * matter how much room there was for it.
 */
private val LargeDimens = LukiaDimens(
    screenPadding = 32.dp,
    bookCellMinWidth = 156.dp,
    gridSpacing = 20.dp,
    gridRowSpacing = 26.dp,
    maxGridWidth = 1840.dp,
    maxDetailWidth = 600.dp,
    maxReaderWidth = 860.dp,
    detailCoverWidth = 220.dp,
    sectionSpacing = 26.dp,
    panelPadding = 14.dp,
)

internal fun dimensFor(widthClass: WindowWidthClass): LukiaDimens = when (widthClass) {
    WindowWidthClass.Compact -> CompactDimens
    WindowWidthClass.Medium -> MediumDimens
    WindowWidthClass.Expanded -> ExpandedDimens
    WindowWidthClass.Large -> LargeDimens
}

val LocalLukiaDimens = staticCompositionLocalOf { CompactDimens }

val LocalWindowWidthClass = staticCompositionLocalOf { WindowWidthClass.Compact }

val LocalWindowHeightClass = staticCompositionLocalOf { WindowHeightClass.Medium }

/**
 * Whether this window is short enough that vertical room is the scarce one -
 * a phone in landscape, or a tablet sharing its screen with something else.
 *
 * The companion to [isWideWindow], and written for the same reason: the
 * comparison was going to be spelled out at every call site that cared, and a
 * comparison spelled out that many times is one that will eventually be spelled
 * out wrongly.
 */
val isShortWindow: Boolean
    @Composable
    get() = LocalWindowHeightClass.current == WindowHeightClass.Compact

/**
 * Whether this window is being pointed at rather than touched, which is the one
 * question most of the app's layout branches are actually asking.
 *
 * Written as a helper because `LocalWindowWidthClass.current != Compact` was
 * repeated at a dozen call sites, and a comparison spelled out that many times is
 * a comparison that will eventually be spelled out wrongly.
 */
val isWideWindow: Boolean
    @Composable
    get() = LocalWindowWidthClass.current != WindowWidthClass.Compact
