package io.lukia.shared.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spacing scale, on a 4dp grid with two half-steps for the gaps that sit
 * between a label and the thing it labels.
 *
 * Small gaps used to be written as literals at every call site, which is how two
 * rows that should have matched came to sit 6dp and 8dp apart. Structural
 * measurements - the inset from the window edge, the gap between sections, a
 * panel's own padding - are not here: those change with the window and live in
 * [LukiaDimens].
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

/** How thick a hairline border is, and the ring around a keyboard-focused control. */
val HairlineWidth: Dp = 1.dp
val FocusRingWidth: Dp = 2.dp

/**
 * The size of the things a user points at.
 *
 * Split from [LukiaDimens] because these answer a different question. Layout
 * metrics ask how much room there is; these ask what is doing the pointing. A
 * finger needs [MinTouchTarget] whatever the window; a mouse cursor is a few
 * pixels across and a 44dp button under one is mostly padding - which is most of
 * what makes a desktop window look like a scaled-up phone.
 *
 * Window width used to stand in for the input device here, as it still does for
 * tooltips and the search field's proportions. It cannot: those are questions
 * about room, and this is not. A 1024dp iPad is pointed at with a finger and was
 * being handed 32dp controls and 24dp icon buttons - under Apple's 44pt floor
 * and Material's 48dp one - and so, for as long as it was turned, was an
 * ordinary phone in landscape at 844dp.
 *
 * So this asks the platform instead, which is the same thing `App` already asks
 * to decide which navigation the app has. A build somebody holds is touched at
 * every window size it can be turned to; a desktop is pointed at whether or not
 * its window is small.
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
