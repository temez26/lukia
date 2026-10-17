package io.lukia.shared.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spacing scale, on a 4dp grid with two half-steps for the gaps that sit
 * between a label and the thing it labels.
 *
 * Small gaps used to be literals at every call site, which is how two rows that
 * should have matched came to sit 6dp and 8dp apart. Structural measurements -
 * the window inset, the gap between sections, a panel's padding - change with
 * the window and live in [LukiaDimens].
 */
object Space {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 6.dp
    val md: Dp = 8.dp
    val lg: Dp = 12.dp
    val xl: Dp = 16.dp
    val xxl: Dp = 24.dp
}

/**
 * Opacities that carry meaning rather than decoration, so that "disabled" or
 * "hovered" looks the same wherever it happens.
 */
object LukiaAlpha {

    /** Anything the user cannot act on right now. Material's own disabled value. */
    const val Disabled = 0.38f

    /** The wash a control takes under the pointer. */
    const val Hover = 0.07f

    /** ...and under a press, for controls that tint rather than scale. */
    const val Pressed = 0.12f

    /** Dimming behind an open menu or panel. */
    const val Scrim = 0.28f

    /** The tint marking the row the reader is currently on. */
    const val Selected = 0.10f

    /**
     * A recessed fill - a search field, a segmented track - over a surface.
     * Slightly translucent so it picks up whatever it is laid on rather than
     * needing a colour per context.
     */
    const val Fill = 0.7f

    /** The wash behind a badge, in the badge's own colour. */
    const val Badge = 0.12f

    /** A chip of chrome laid straight over artwork, which has to stay legible. */
    const val OverArtwork = 0.88f

    /**
     * The edge of a control that has no fill to mark where it is - the secondary
     * button, and nothing else so far.
     *
     * In the content colour at this strength rather than in `outlineVariant`,
     * which separates two rows of one surface and is deliberately invisible next
     * to a boundary. A control's edge is held to 3:1 against the surface behind
     * it, and this is the lowest opacity reaching that in every palette.
     */
    const val Border = 0.5f
}

/** Shadow depths. Deliberately shallow: depth here is for ordering, not for drama. */
object LukiaElevation {

    /** A sheet in the reader, which is paper on a desk rather than a floating card. */
    val page: Dp = 1.dp

    val card: Dp = 3.dp

    /** What a card rises to under the pointer. */
    val cardHovered: Dp = 12.dp

    /** The cover on the detail screen, which is the subject of the whole screen. */
    val feature: Dp = 10.dp

    /** An overlay panel, which has a dimmed screen rather than a shadow to separate it. */
    val panel: Dp = 16.dp
}

/**
 * Room a scrolling page leaves around itself for the chrome that floats over it.
 *
 * Not spacing, and so not in [Space]: these are the height of something else -
 * the back control a screen scrolls under, the edge a column must not end hard
 * against - rather than the gap between two things on the page.
 *
 * Here because each was written out once per screen: the settings, book detail
 * and statistics bodies each carried a private `TopChromeClearance` of 68dp
 * under identical documentation, and the library grid, search results and home
 * rows each carried their own 24dp tail. Three copies of a number is three
 * chances for two screens under the same control to stop agreeing about where it
 * ends.
 */
object Clearance {

    /** Past the last group of a scrolling column. */
    val pageBottom: Dp = 32.dp

    /** ...and past the last row of a scrolling list. */
    val listBottom: Dp = 24.dp
}

/**
 * How wide the app sets a sentence that is standing in for a screen.
 *
 * One measure for both of the states that occupy an empty screen, since they
 * take the same slot and are read the same way. `EmptyState` capped at 360dp and
 * `ErrorState` at 340dp, which is a difference nobody chose and nobody can see
 * except when a screen shows one after the other.
 */
val StateMeasure: Dp = 360.dp

/** How thick a hairline border is, and the ring around a keyboard-focused control. */
val HairlineWidth: Dp = 1.dp
val FocusRingWidth: Dp = 2.dp

/**
 * The size of the things a user points at.
 *
 * Split from [LukiaDimens] because these answer a different question: layout
 * metrics ask how much room there is, these ask what is doing the pointing. A
 * finger needs [MinTouchTarget] whatever the window; a 44dp button under a mouse
 * cursor is mostly padding, which is most of what makes a desktop window look
 * like a scaled-up phone.
 *
 * Window width used to stand in for the input device, as it still does for
 * tooltips and the search field's proportions. It cannot here: a 1024dp iPad is
 * pointed at with a finger and was handed 32dp controls and 24dp icon buttons -
 * under Apple's 44pt floor and Material's 48dp one - and so was an ordinary
 * phone turned to landscape at 844dp.
 *
 * So this asks the platform instead, as `App` already does to decide which
 * navigation the app has: a build somebody holds is touched at every window size
 * it can be turned to, and a desktop is pointed at however small its window.
 */
@Immutable
data class LukiaMetrics(
    /** A button, a segmented track, a text stepper: the app's standard control. */
    val controlHeight: Dp,
    /** A secondary control that sits beside a standard one - the search field. */
    val controlHeightSmall: Dp,
    /** Edge of a square icon button. */
    val iconButton: Dp,
    /** ...and of one tucked into a card corner or a text field. */
    val iconButtonSmall: Dp,
    /** The glyph inside an [iconButton]. */
    val icon: Dp,
    /** The glyph on a menu row, or beside a label. */
    val iconSmall: Dp,
    /** Minimum height of a menu row, a chapter row, a settings row. */
    val rowHeight: Dp,
)

private val TouchMetrics = LukiaMetrics(
    controlHeight = MinTouchTarget,
    controlHeightSmall = 38.dp,
    iconButton = MinTouchTarget,
    iconButtonSmall = 32.dp,
    icon = 20.dp,
    iconSmall = 18.dp,
    rowHeight = MinTouchTarget,
)

private val PointerMetrics = LukiaMetrics(
    controlHeight = 32.dp,
    controlHeightSmall = 28.dp,
    iconButton = 32.dp,
    iconButtonSmall = 24.dp,
    icon = 17.dp,
    iconSmall = 15.dp,
    rowHeight = 30.dp,
)

internal fun metricsFor(touch: Boolean): LukiaMetrics =
    if (touch) TouchMetrics else PointerMetrics

val LocalLukiaMetrics = staticCompositionLocalOf { TouchMetrics }
