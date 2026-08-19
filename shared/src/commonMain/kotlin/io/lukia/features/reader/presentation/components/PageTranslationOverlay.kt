package io.lukia.features.reader.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lukia.core.presentation.displayShortMessage
import io.lukia.features.reader.domain.TextQuad
import io.lukia.features.translate.domain.PageSentence
import io.lukia.features.translate.presentation.PassageTranslation
import io.lukia.features.translate.presentation.SentenceTranslations

/**
 * How far the covering panel is drawn outside the words it covers, so no
 * ascender or descender shows around the edge of it.
 */
private val Bleed: Dp = 2.dp

/** Where the translation is set inside its panel. */
private val PanelPadding: Dp = 4.dp

/**
 * What has been translated on one page of a fixed-layout document, drawn over
 * the page itself.
 *
 * A PDF's page is a picture. Nothing here can re-set it the way a reflowable
 * book is re-set, so a translation is laid over the words it stands for: the
 * original is covered in the page's own colour and the new sentence written in
 * its place, sitting exactly where the reader was already looking.
 *
 * Everything is measured in the page's own units and scaled by [scale], which
 * is the one number that relates the file to whatever width the page is
 * currently drawn at - so this stays correct through a pinch, a resize and a
 * change of reading width without recalculating anything.
 */
@Composable
internal fun PageTranslationOverlay(
    sentences: List<PageSentence>,
    translations: SentenceTranslations,
    scale: Float,
    pageColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    // A sentence nobody has touched is left exactly as the page has it - and a
    // page nobody has touched at all, which is most of them, walks off here
    // without looking at its sentences.
    if (translations.isEmpty) return

    // Sentences and single words together, because what is done with either is
    // the same: mark where it is, and cover it once there is something to put in
    // its place. A word is only ever on the page while its sentence has not been
    // translated over the top of it.
    val marked = remember(sentences, translations) {
        sentences.flatMap { sentence ->
            val whole = translations[sentence.index]

            if (whole != null) {
                listOf(MarkedPassage(sentence.quads, sentence.bounds, whole))
            } else {
                sentence.words.mapNotNull { word ->
                    translations.words(sentence.index)[word.index]?.let {
                        MarkedPassage(word.quads, word.bounds, it)
                    }
                }
            }
        }
    }

    val markColors = passageMarkColors()

    // Read where it is drawn rather than here: read during composition it would
    // recompose the page on every frame of the sweep.
    val sweep = passageSweep(marked.any { it.state is PassageTranslation.Loading })

    Box(
        modifier = modifier
            .fillMaxSize()
            // Every mark in one pass rather than a full-page layer per
            // sentence: they all draw into the same page.
            //
            // The same soft highlight the set text is marked with, so moving
            // between a PDF and an EPUB is not moving between two different
            // readers. Faint enough to leave somebody else's typesetting
            // perfectly readable underneath it.
            .drawBehind {
                marked.forEach { passage ->
                    // A passage with something to put on the page brings its own
                    // highlight with it - see TranslationPanel. Drawing one here
                    // as well would leave the wider of the two showing round the
                    // edges of the narrower.
                    if (passage.state.panel() != null) return@forEach

                    val style = passage.state.markStyle() ?: return@forEach

                    drawPassageHighlight(
                        lines = passage.quads.map { it.asLine(scale) },
                        style = style,
                        colors = markColors,
                        sweep = sweep.value
                    )
                }
            }
    ) {
        marked.forEach { passage ->
            val panel = passage.state.panel()

            // Nothing is drawn over a sentence still being translated: the
            // reader can still read the original underneath while they wait.
            AnimatedVisibility(
                visible = panel != null,
                enter = fadeIn(tween(PANEL_FADE_MILLIS)),
                exit = fadeOut(tween(PANEL_FADE_MILLIS))
            ) {
                // Held after it goes, so the panel fades out with words in it
                // rather than emptying first and then fading.
                val shown = panel ?: return@AnimatedVisibility

                TranslationPanel(
                    // The line rather than the letters, and the same line the
                    // highlight would have covered - so the panel sits exactly
                    // where the mark would have been instead of inside it.
                    bounds = passage.bounds.asLine(),
                    // Measured from the shortest of the passage's lines, which is
                    // one line - not the block they add up to. See below.
                    glyphHeight = passage.quads.minOf { it.height },
                    text = shown.text,
                    detail = shown.detail,
                    textColor = if (shown.failed) colors.error else colors.onSurface,
                    tint = markColors.of(
                        passage.state.markStyle() ?: PassageMarkStyle.Translated
                    ),
                    scale = scale,
                    pageColor = pageColor
                )
            }
        }
    }
}

/** What a panel over a sentence has to say, if anything. */
private data class PanelContent(
    val text: String,
    val detail: String?,
    val failed: Boolean
)

/**
 * A failure gets a panel of its own rather than only a red wash.
 *
 * Whatever went wrong - a key refused, an account with nothing left, a passage
 * the service would not take - is something the reader can act on, and a tinted
 * rectangle tells them none of it. Without this, a translation that failed and
 * one still in flight look exactly alike.
 */
private fun PassageTranslation.panel(): PanelContent? = when (this) {
    // A partly written sentence goes over the page as it arrives, the same as a
    // finished one. Until the first words come back there is nothing to cover
    // the original with, and the tint alone says a translation is on its way.
    is PassageTranslation.Loading -> partial?.let {
        PanelContent(text = it, detail = null, failed = false)
    }

    is PassageTranslation.Failed -> PanelContent(
        text = error.displayShortMessage(),
        detail = null,
        failed = true
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
private fun TranslationPanel(
    bounds: TextQuad,
    glyphHeight: Float,
    text: String,
    detail: String?,
    textColor: Color,
    tint: Color,
    scale: Float,
    pageColor: Color
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
            .width(width + Bleed * 2)
            .clip(RoundedCornerShape(PanelRadius))
            // The page's own colour first, because what is underneath is the
            // original and it has to stop showing through. The mark goes over
            // that rather than behind the whole panel: the panel is the
            // highlight here, which is what keeps the two from disagreeing about
            // where the passage is.
            .background(pageColor)
            .background(tint.copy(alpha = PANEL_TINT))
            .padding(PanelPadding)
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = textSize,
                lineHeight = textSize * 1.25f
            ),
            textAlign = TextAlign.Start
        )

        if (detail != null) {
            Text(
                text = detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = textSize * 0.8f,
                    lineHeight = textSize,
                    fontStyle = FontStyle.Italic
                ),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/**
 * The size the page's type was set at, worked back from the boxes it reports.
 *
 * What a PDF gives per glyph is roughly the cap height, which measurement
 * against real files puts near 0.58 of the size the type was set at - the same
 * figure the block detection is built on, see `BLOCK_GAP_RATIO`. So the type is
 * a little under twice the box.
 *
 * Getting this wrong is what made a translation come out at less than half the
 * size of the words it was covering: the box was being read as though it were
 * the whole line, and then shrunk again on top of that.
 */
private const val TYPE_OF_GLYPHS = 1.72f

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
 * somebody who is waiting for it.
 */
private const val PANEL_FADE_MILLIS = 160

/**
 * One passage of a page the reader has asked about - a sentence, or a single
 * word inside one - and what has become of it.
 *
 * The two are the same thing to draw: some rectangles to mark, and somewhere to
 * put the answer once it arrives. Keeping them apart here would be two copies of
 * the same loop that differed only in where the rectangles came from.
 */
private data class MarkedPassage(
    val quads: List<TextQuad>,
    val bounds: TextQuad,
    val state: PassageTranslation
)

/**
 * How much taller than its letters a line is, as a fraction of their height.
 *
 * A glyph box a PDF reports is roughly the cap height, which measurement against
 * real files puts near 0.58 of the size the type was set at - so the line around
 * it is close to twice as tall. Split unevenly: there is more room under a
 * baseline than over a capital.
 */
private const val LINE_ABOVE_GLYPHS = 0.26f
private const val LINE_BELOW_GLYPHS = 0.34f

/**
 * The line these letters were set on, in the page's own units.
 *
 * One definition, used by the mark and by the panel alike - they cover the same
 * passage and any disagreement between them shows as one sticking out from
 * behind the other.
 */
private fun TextQuad.asLine(): TextQuad = TextQuad(
    left = left,
    top = top - height * LINE_ABOVE_GLYPHS,
    right = right,
    bottom = bottom + height * LINE_BELOW_GLYPHS
)

private fun TextQuad.asLine(scale: Float): Rect = asLine().let { line ->
    Rect(
        left = line.left * scale,
        top = line.top * scale,
        right = line.right * scale,
        bottom = line.bottom * scale
    )
}
