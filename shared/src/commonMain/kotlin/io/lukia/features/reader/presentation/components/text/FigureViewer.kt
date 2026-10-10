package io.lukia.features.reader.presentation.components.text

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.lukia.resources.Res
import io.lukia.resources.action_close
import io.lukia.resources.figure_close
import io.lukia.shared.presentation.components.controls.IconButtonVariant
import io.lukia.shared.presentation.components.controls.LukiaIconButton
import io.lukia.shared.presentation.components.icons.PlusGlyph
import io.lukia.shared.presentation.components.menu.popoverTransition
import io.lukia.shared.presentation.components.menu.rememberPopoverProgress
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import org.jetbrains.compose.resources.stringResource

/** How dark the book behind the illustration goes. Nearly out: the picture is the point. */
private const val ViewerScrimAlpha = 0.92f

/** Room left around the illustration, so it reads as held up rather than as a new screen. */
private val ViewerPadding: Dp = 24.dp

/** How far a double press takes it, and the range a pinch may run through. */
private const val DoublePressScale = 2.5f
private const val MinScale = 1f
private const val MaxScale = 8f

/**
 * One illustration, on its own over the book.
 *
 * A plate in an EPUB is laid out to the measure of a page of prose, and a
 * diagram, map or page of music at that width is a picture of something
 * unreadable. The image size setting acts on every figure at once, so a press
 * lifts this one out to the size of the window instead.
 *
 * Fit against the whole window rather than drawn at its own size, so the
 * sixteen-pixel ornament and the two-thousand-pixel plate both arrive as large
 * as they usefully can.
 *
 * A [Popup] rather than something inside the page: the figure sits in a lazy
 * list inside the reader's chrome, where it would be clipped by the page it came
 * from. Focusable, unlike the app's menus, because it covers everything and
 * should take Escape and Back rather than leaving them to close the book.
 *
 * Arrives as every panel does - see [popoverTransition]. The scrim only fades: a
 * full-window fill scaled from 0.92 would show a band of unfaded page down each
 * edge on the way in.
 *
 * Draws nothing once closed and settled, so [FigureBlock] can leave it in the
 * tree and let it animate itself out.
 */
@Composable
internal fun FigureViewer(
    visible: Boolean,
    image: ImageBitmap,
    description: String?,
    onDismiss: () -> Unit,
) {
    val progress = rememberPopoverProgress(visible, label = "figureViewer")

    if (!visible && progress == 0f) return

    val dismissSource = remember { MutableInteractionSource() }

    Popup(
        popupPositionProvider = FullWindow,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                // The margin around the illustration is a way out too, and the
                // named one: the picture itself answers a press by closing only
                // while it is unzoomed, which is not something to describe to a
                // screen reader as a control.
                .clickable(
                    interactionSource = dismissSource,
                    indication = null,
                    onClickLabel = stringResource(Res.string.figure_close),
                    onClick = onDismiss,
                ),
        ) {
            // A layer of its own rather than a background on the box above,
            // which would fade the picture and the button along with it.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress }
                    .background(Color.Black.copy(alpha = ViewerScrimAlpha)),
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    // From the middle of the window, because that is where it
                    // ends up: nothing on screen anchors it, unlike a menu
                    // growing out of the control that opened it.
                    .popoverTransition(progress, TransformOrigin.Center),
            ) {
                ZoomableFigure(image = image, description = description, onDismiss = onDismiss)
            }

            LukiaIconButton(
                label = stringResource(Res.string.action_close),
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    // Fades without scaling. It belongs to the corner of the
                    // window rather than to the picture, and a corner control
                    // scaled about the middle travels further on its way in than
                    // anything this small should.
                    .graphicsLayer { alpha = progress }
                    .padding(LocalLukiaDimens.current.screenPadding),
                variant = IconButtonVariant.Glass,
            ) { tint, glyph ->
                // The same plus turned on its side that every other panel in the
                // app closes with.
                PlusGlyph(tint = tint, modifier = glyph.graphicsLayer { rotationZ = 45f })
            }
        }
    }
}

/**
 * The illustration itself: as large as the window allows, and larger again on
 * request.
 *
 * Two ways in to the same scale, for two kinds of device. A pinch is what a
 * phone does and a desktop cannot; a double press is what a mouse has, and is a
 * step rather than a slide because a mouse cannot say how far. Both settle
 * through the same pair of values, so a pinch after a double press continues
 * from where the press left it.
 *
 * A pinch zooms about the fingers rather than the middle of the window - the
 * difference between reading the corner of a map and watching it slide away.
 * Panning is bounded by how much of the picture is actually off-screen, so it
 * cannot be pushed out of the window and lost.
 *
 * An unzoomed press closes, which is what a picture held up over a book should
 * do. Once it has been zoomed it does not - at that point a press is somebody
 * aiming at the picture, and the way out is the button, Escape or Back.
 */
@Composable
private fun BoxScope.ZoomableFigure(
    image: ImageBitmap,
    description: String?,
    onDismiss: () -> Unit,
) {
    var scale by remember { mutableStateOf(MinScale) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    val middle = Offset(viewport.width / 2f, viewport.height / 2f)
    val drawn = image.drawnInside(viewport)

    /** Takes the scale, and as much of the wanted pan as there is picture to reach. */
    fun settle(wanted: Float, panned: Offset) {
        scale = wanted

        // Against what is drawn rather than against the window: an illustration
        // fitted into a window it does not share proportions with has bands of
        // nothing above or below it, and panning into those is panning into
        // emptiness.
        val loose = Offset(
            x = ((drawn.width * wanted - viewport.width) / 2f).coerceAtLeast(0f),
            y = ((drawn.height * wanted - viewport.height) / 2f).coerceAtLeast(0f),
        )

        offset = Offset(
            x = panned.x.coerceIn(-loose.x, loose.x),
            y = panned.y.coerceIn(-loose.y, loose.y),
        )
    }

    val transform = rememberTransformableState { centroid, zoomChange, panChange, _ ->
        val wanted = (scale * zoomChange).coerceIn(MinScale, MaxScale)
        // What it takes to leave the point under the fingers where it was: the
        // layer scales about its own middle, so a point that far out from the
        // middle moves by the change in scale and has to be moved back.
        val held = (centroid - middle) * (scale - wanted)

        settle(wanted, offset + held + panChange)
    }

    Image(
        bitmap = image,
        contentDescription = description?.takeIf { it.isNotBlank() },
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .align(Alignment.Center)
            .fillMaxSize()
            .padding(ViewerPadding)
            .onSizeChanged { viewport = it }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
            .transformable(transform)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        settle(if (scale > MinScale) MinScale else DoublePressScale, Offset.Zero)
                    },
                    onTap = { if (scale <= MinScale) onDismiss() },
                )
            },
    )
}

/**
 * How large this picture is actually drawn inside [viewport], which is the fit
 * every side of it is measured against.
 *
 * `ContentScale.Fit` scales up as readily as down - that is what turns a
 * sixteen-pixel ornament into something worth having opened - so this is the
 * smaller of the two ratios either way.
 */
private fun ImageBitmap.drawnInside(viewport: IntSize): Size {
    if (width == 0 || height == 0) return Size.Zero

    val fit = minOf(viewport.width.toFloat() / width, viewport.height.toFloat() / height)

    return Size(width * fit, height * fit)
}

/**
 * The whole window, from its top left corner.
 *
 * A popup is positioned against an anchor by default, and this one has none - it
 * is not hanging off a control but covering everything.
 */
private val FullWindow = object : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}
