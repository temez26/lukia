package io.lukia.features.reader.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lukia.core.presentation.displayShortMessage
import io.lukia.features.reader.domain.TextQuad
import io.lukia.features.translate.presentation.PassageTranslation
import io.lukia.shared.presentation.theme.LukiaMotion

// The translation itself, drawn over the page it replaces - see
// PageTranslationOverlay for which passages get one.

/**
 * How far the covering panel is drawn outside the words it covers, so no
 * ascender or descender shows around the edge of it.
 */
private val Bleed: Dp = 2.dp

/** Where the translation is set inside its panel. */
private val PanelPadding: Dp = 4.dp

/** What a panel over a sentence has to say, if anything. */
internal data class PanelContent(
    val text: String,
    val detail: String?,
    val failed: Boolean,
)

/**
 * A failure gets a panel of its own rather than only a red wash.
 *
 * Whatever went wrong - a key refused, an account with nothing left, a passage
 * the service would not take - is something the reader can act on, and a tinted
 * rectangle tells them none of it. Without this, a translation that failed and
 * one still in flight look exactly alike.
 */
internal fun PassageTranslation.panel(): PanelContent? = when (this) {
    // A partly written sentence goes over the page as it arrives, the same as a
    // finished one. Until the first words come back there is nothing to cover
    // the original with, and the tint alone says a translation is on its way.
    is PassageTranslation.Loading -> partial?.let {
        PanelContent(text = it, detail = null, failed = false)
    }

    is PassageTranslation.Failed -> PanelContent(
        text = error.displayShortMessage(),
        detail = null,
        failed = true,
    )

    is PassageTranslation.Ready -> takeIf { !showingOriginal }?.let {
        PanelContent(text = translation.text, detail = translation.note, failed = false)
    }
}

/**
 * The translation, over the sentence it replaces.
 *
 * Set to the width of the block the original occupied and left to be as tall as
 * it needs: a sentence rarely comes out the same length in another language, and
 * a panel clipped to the original's height would cut the translation off. Growing
 * downwards over the lines below is the honest compromise - the page underneath
 * is a picture and cannot make room.
 */
@Composable
internal fun TranslationPanel(
    bounds: TextQuad,
    available: Dp,
    glyphHeight: Float,
    text: String,
    detail: String?,
    textColor: Color,
    tint: Color,
    scale: Float,
    pageColor: Color,
    onTap: () -> Unit,
) {
    val density = LocalDensity.current

    val left = with(density) { (bounds.left * scale).toDp() }
    val top = with(density) { (bounds.top * scale).toDp() }
    val width = with(density) { (bounds.width * scale).toDp() }

    // Set at the size the page is set at, worked back from the letters - see
    // TYPE_OF_GLYPHS. Measured from one line of the original, never from the
    // block a sentence covers: a sentence running down fifteen lines has bounds
    // fifteen lines tall, and type set to that is a single word filling the page.
    //
    // Clamped at both ends, because a page can be laid out at any size at all: a
    // display line set enormously does not want its translation set enormously
    // too, and a footnote at five points wants something somebody can read.
    val textSize = with(density) {
        (glyphHeight * TYPE_OF_GLYPHS * scale)
            .toSp()
            .value
            .coerceIn(MIN_TEXT_SP, MAX_TEXT_SP)
            .sp
    }

    Column(
        modifier = Modifier
            .offset(x = left - Bleed, y = top - Bleed)
            // A hold means the same as a tap here. Once the words underneath are
            // covered there is nothing else in this rectangle to ask about, so
            // there is no second thing for a second gesture to mean.
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() }, onLongPress = { onTap() })
            }
            // At least as wide as what it replaces, and free to be wider.
            //
            // A word is the case that needs this: its panel is only as wide as
            // the word underneath it, and a translation is very often longer
            // than the word it stands for - so held to that width it wrapped,
            // and one word became two lines sitting over the line below. Bounded
            // by what is left of the page so it grows into the margin rather
            // than off the edge of it.
            .widthIn(
                min = width + Bleed * 2,
                max = (available - left + Bleed).coerceAtLeast(width + Bleed * 2),
            )
            .clip(RoundedCornerShape(PanelRadius))
            // The page's own colour first, because what is underneath is the
            // original and it has to stop showing through. The mark goes over
            // that rather than behind the whole panel: the panel is the
            // highlight here, which is what keeps the two from disagreeing about
            // where the passage is.
            .background(pageColor)
            .background(tint.copy(alpha = PANEL_TINT))
            .padding(PanelPadding),
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = textSize,
                lineHeight = textSize * 1.25f,
            ),
            textAlign = TextAlign.Start,
        )

        if (detail != null) {
            Text(
                text = detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = textSize * 0.8f,
                    lineHeight = textSize,
                    fontStyle = FontStyle.Italic,
                ),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * The size the page's type was set at, worked back from the boxes it reports.
 *
 * There is no honest way to derive this. What a renderer calls a glyph's bounds
 * differs between them - some give the ink, some the em box, some the whole line
 * - and the file itself is not asked. So this is calibrated against real pages
 * rather than reasoned out, and it is the one number here to reach for when a
 * translation comes out the wrong size beside the words it covers.
 *
 * A little over parity. Under it the translation reads as a caption stuck on the
 * page; far over it, which is where this landed on the first attempt, the words
 * spill out of the line they belong to.
 */
private const val TYPE_OF_GLYPHS = 1.15f

/**
 * What a translation is never set smaller or larger than, whatever the page it
 * is covering does. Wide enough now to leave a heading at a heading's size,
 * which the old ceiling did not.
 */
private const val MIN_TEXT_SP = 10f
private const val MAX_TEXT_SP = 44f

/** How much of the mark's colour a panel carries, being the highlight itself. */
private const val PANEL_TINT = 0.16f

/** Round enough to read as the same mark the set text gets. */
private val PanelRadius: Dp = 6.dp

/**
 * How long a panel takes to arrive over the words it covers. Short enough that
 * it reads as the answer landing rather than as an animation being played at
 * somebody who is waiting for it, which is exactly what the app's quickest
 * duration is for - see [LukiaMotion].
 */
internal const val PANEL_FADE_MILLIS = LukiaMotion.QuickMillis
