package io.lukia.features.bookdetail.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import io.lukia.core.domain.CoverImage
import io.lukia.shared.presentation.components.BookCover
import io.lukia.shared.presentation.components.OpenBookGlyph
import io.lukia.shared.presentation.components.ReadingProgressBar
import io.lukia.shared.presentation.components.focusRing
import io.lukia.shared.presentation.components.rememberHoverFraction
import io.lukia.shared.presentation.theme.LukiaAlpha
import io.lukia.shared.presentation.theme.LukiaElevation
import io.lukia.shared.presentation.theme.LukiaMotion
import io.lukia.shared.presentation.theme.isWideWindow

/** How far the cover dims behind the read control, at full reveal. */
private const val ScrimAlpha = 0.42f

/** Diameter of the read control, as a fraction of the cover's width. */
private const val ReadControlFraction = 0.30f

/**
 * A book's cover, which is also the way into the book.
 *
 * The cover is the button rather than carrying one: this screen exists to open
 * one book, and a separate Read control below the artwork is a second thing to
 * aim at for an action the cover is already standing for. Pressing it opens the
 * book; a long press opens the same actions menu the artwork carries elsewhere
 * in the app.
 *
 * Where the reader got to is drawn as a bar flush along the bottom edge, inside
 * the cover's own corner radius, so it reads as part of the artwork rather than
 * as a widget parked underneath it. It is present whenever the book has been
 * opened, hover or not - it is information, not an affordance.
 *
 * The play control appears differently depending on what is doing the pointing.
 * Under a mouse the cover dims and the control fades up, so at rest the artwork
 * is unobscured. On a touch screen there is no hover to reveal anything with, so
 * the control sits there at rest with only its own backing behind it - a
 * permanently dimmed cover would be a poor trade for an affordance a tap would
 * have found anyway.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReadableCover(
    coverImage: CoverImage?,
    title: String,
    /** How far through, 0 to 1. Null for a book that has never been opened. */
    progress: Float?,
    enabled: Boolean,
    onRead: () -> Unit,
    onLongPress: () -> Unit,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val hover = rememberHoverFraction(interactionSource)
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = MaterialTheme.shapes.large

    val focusReveal by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = LukiaMotion.quick(),
        label = "coverFocusReveal",
    )

    // A pointer reveals the control; a finger has nothing to reveal it with, so
    // it is already there.
    val restingReveal = if (isWideWindow) 0f else 1f
    val reveal = maxOf(hover, focusReveal, restingReveal)

    Box(
        modifier = modifier
            .width(width)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClickLabel = "Read $title",
                onClick = onRead,
                onLongClickLabel = "Book actions",
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                },
            )
            .focusRing(interactionSource, shape)
            .semantics { contentDescription = title },
    ) {
        BookCover(
            coverImage = coverImage,
            title = title,
            shape = shape,
            elevation = LukiaElevation.feature,
            modifier = Modifier.fillMaxWidth(),
        )

        // Everything laid over the artwork shares the cover's clip, so the scrim
        // and the progress bar stop exactly where the rounded corners do.
        //
        // matchParentSize rather than fillMaxSize: the cover is what decides how
        // tall this is, through its aspect ratio, and a child that measured
        // itself against the parent would have nothing to measure against.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape),
        ) {
            if (enabled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Only the mouse case dims; the touch case leaves the
                        // artwork alone and lets the control's own backing carry it.
                        .graphicsLayer { alpha = maxOf(hover, focusReveal) }
                        .background(Color.Black.copy(alpha = ScrimAlpha)),
                )

                ReadControl(
                    reveal = reveal,
                    diameter = width * ReadControlFraction,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            if (progress != null) {
                ReadingProgressBar(
                    fraction = progress,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }
        }
    }
}

/**
 * The open-book mark, on a translucent disc so it reads against both a pale
 * cover and a dark one without either being dimmed for its sake.
 */
@Composable
private fun ReadControl(
    reveal: Float,
    diameter: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer {
                alpha = reveal
                // Arrives from slightly under full size, which is what makes it
                // read as coming forward rather than simply switching on.
                val scale = 0.86f + 0.14f * reveal
                scaleX = scale
                scaleY = scale
            }
            .size(diameter)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = LukiaAlpha.OverArtwork)),
    ) {
        OpenBookGlyph(
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(diameter * 0.54f),
        )
    }
}
