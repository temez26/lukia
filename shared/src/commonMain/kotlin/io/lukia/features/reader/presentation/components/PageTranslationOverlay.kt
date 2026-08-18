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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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

    val marked = remember(sentences, translations) {
        sentences.mapNotNull { sentence ->
            translations[sentence.index]?.let { sentence to it }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Every mark in one pass rather than a full-page layer per
            // sentence: they all draw into the same page, and the tint says
            // which of the three things is happening to each of them.
            .drawBehind {
                marked.forEach { (sentence, state) ->
                    val tint = when (state) {
                        PassageTranslation.Loading -> colors.onSurface.copy(alpha = PENDING_TINT)
                        is PassageTranslation.Failed -> colors.error.copy(alpha = FAILED_TINT)
                        is PassageTranslation.Ready -> colors.primary.copy(alpha = TRANSLATED_TINT)
                    }

                    sentence.quads.forEach { quad -> drawQuad(quad, scale, tint) }
                }
            }
    ) {
        marked.forEach { (sentence, state) ->
            val panel = state.panel()

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
                    bounds = sentence.bounds,
                    // The shortest of the sentence's lines, which is a line -
                    // not the block they add up to. See TranslationPanel.
                    lineHeight = sentence.quads.minOf { it.height },
                    text = shown.text,
                    detail = shown.detail,
                    textColor = if (shown.failed) colors.error else colors.onSurface,
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
    PassageTranslation.Loading -> null

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
    lineHeight: Float,
    text: String,
    detail: String?,
    textColor: Color,
    scale: Float,
    pageColor: Color
) {
    val density = LocalDensity.current

    val left = with(density) { (bounds.left * scale).toDp() }
    val top = with(density) { (bounds.top * scale).toDp() }
    val width = with(density) { (bounds.width * scale).toDp() }

    // Sized from one line of the original, never from the block the sentence
    // covers. A sentence running down fifteen lines has bounds fifteen lines
    // tall, and type set to that is a single word filling the page.
    //
    // Clamped as well, because a page can be laid out at any size at all: a
    // heading set in 40pt does not want its translation at 40pt, and a footnote
    // at 5pt wants something somebody can actually read.
    val textSize = with(density) {
        (lineHeight * scale * TEXT_FILL).toSp().value.coerceIn(MIN_TEXT_SP, MAX_TEXT_SP).sp
    }

    Column(
        modifier = Modifier
            .offset(x = left - Bleed, y = top - Bleed)
            .width(width + Bleed * 2)
            .clip(RoundedCornerShape(3.dp))
            // The page's own colour, so the panel reads as part of the page
            // rather than as a note stuck on top of it.
            .background(pageColor)
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
 * How much of a line's height the translation's type fills.
 *
 * A quad is the whole line box, ascender to descender, and type set at that
 * size would be visibly larger than the page's own. This is roughly where a
 * translated line matches the original it is covering.
 */
private const val TEXT_FILL = 0.78f

/**
 * What a translation is never set smaller or larger than, whatever the page it
 * is covering does. The point of the panel is that somebody can read it.
 */
private const val MIN_TEXT_SP = 9f
private const val MAX_TEXT_SP = 22f

/**
 * How faint each mark is. A sentence being worked on has to be findable again
 * without the page turning into a highlighter drawing.
 */
private const val PENDING_TINT = 0.10f
private const val TRANSLATED_TINT = 0.14f
private const val FAILED_TINT = 0.16f

/**
 * How long a panel takes to arrive over the words it covers. Short enough that
 * it reads as the answer landing rather than as an animation being played at
 * somebody who is waiting for it.
 */
private const val PANEL_FADE_MILLIS = 160

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawQuad(
    quad: TextQuad,
    scale: Float,
    color: Color
) {
    val bleed = Bleed.toPx()

    drawRect(
        color = color,
        topLeft = Offset(quad.left * scale - bleed, quad.top * scale - bleed),
        size = Size(quad.width * scale + bleed * 2, quad.height * scale + bleed * 2)
    )
}
