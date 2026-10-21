package io.lukia.shared.presentation.components.cover

import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import io.lukia.shared.presentation.theme.LukiaMotion

/** No darker than this need a tone be; past it, white text on it already reads. */
private const val ToneLuminance = 0.12f

/** How far towards black each step of darkening goes. */
private const val DarkeningStep = 0.15f

/** A cover that is mostly black still has a tone a step up from it, rather than none. */
private const val MaxSteps = 12

/**
 * A book's colour, deep enough for white text to be read on: the average of its
 * cover, darkened. For a background drawn in the book's own colour - the top of
 * Home, behind the book being read.
 *
 * Taken from the cover's backdrop, which is already decoded and a couple of
 * dozen pixels across, so finding it costs nothing worth measuring. A book with
 * no artwork has the colour its generated cover is drawn in.
 *
 * Changes from one book's colour to the next rather than jumping.
 */
@Composable
fun rememberCoverTone(cover: CoverArtSource?, title: String): Color {
    val art = rememberCoverArt(cover)
    val target = remember(art, title) {
        val base = (art as? CoverArt.Artwork)?.cover?.backdrop?.averageColor() ?: bookCoverColor(title)
        base.deepened()
    }
    val tone by animateColorAsState(targetValue = target, animationSpec = LukiaMotion.standard(), label = "coverTone")

    return tone
}

private fun ImageBitmap.averageColor(): Color? {
    val count = width * height
    if (count <= 0) return null

    val pixels = IntArray(count)
    readPixels(pixels)

    var red = 0f
    var green = 0f
    var blue = 0f
    for (pixel in pixels) {
        val color = Color(pixel)
        red += color.red
        green += color.green
        blue += color.blue
    }

    return Color(red / count, green / count, blue / count)
}

private fun Color.deepened(): Color {
    var tone = this
    var steps = 0
    while (tone.luminance() > ToneLuminance && steps < MaxSteps) {
        tone = lerp(tone, Color.Black, DarkeningStep)
        steps++
    }
    return tone
}
