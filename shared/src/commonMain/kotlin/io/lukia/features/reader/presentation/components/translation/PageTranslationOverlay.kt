package io.lukia.features.reader.presentation.components.translation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import io.lukia.features.reader.domain.page.PageSentence
import io.lukia.features.reader.domain.page.TextQuad
import io.lukia.features.reader.domain.page.union
import io.lukia.features.reader.presentation.components.passages.PassageBand
import io.lukia.features.reader.presentation.components.passages.PassageMarkStyle
import io.lukia.features.reader.presentation.components.passages.drawPassageBands
import io.lukia.features.reader.presentation.components.passages.markStyle
import io.lukia.features.reader.presentation.components.passages.passageMarkColors
import io.lukia.features.reader.presentation.components.passages.passageSweep
import io.lukia.features.reader.presentation.translation.PassageTranslation
import io.lukia.features.reader.presentation.translation.SentenceTranslations
import io.lukia.shared.presentation.theme.LukiaMotion

/**
 * What has been translated on one page of a fixed-layout document, drawn over
 * the page itself.
 *
 * A PDF's page is a picture, and nothing here can re-set it the way a reflowable
 * book is re-set. So a translation is laid over the words it stands for: the
 * original covered in the page's own colour, the new sentence written in its
 * place, exactly where the reader was already looking.
 *
 * Everything is measured in the page's own units and scaled by [scale], the one
 * number relating the file to the width it is currently drawn at - so this stays
 * correct through a pinch, a resize and a change of reading width without
 * recalculating anything.
 */
@Composable
internal fun PageTranslationOverlay(
    sentences: List<PageSentence>,
    translations: SentenceTranslations,
    scale: Float,
    pageColor: Color,
    modifier: Modifier = Modifier,
    /**
     * Somebody pointed at a translation, at this point of the page in the file's
     * own units.
     *
     * The panel answers for itself rather than letting the tap fall through to
     * the page, because it is not drawn inside the words it stands for: it
     * starts at their corner and runs as wide and as far down as the
     * translation needs. Most of what the reader can see of it is therefore over
     * some other part of the page, or over no part of it at all - so a tap there
     * reached the page and missed the passage, and putting the book's own words
     * back meant hunting for whatever was left uncovered. A single word was
     * nearly impossible: its panel is several times the size of the word.
     */
    onTranslationPointedAt: (x: Float, y: Float, held: Boolean) -> Unit = { _, _, _ -> },
) {
    val colors = MaterialTheme.colorScheme

    // A sentence nobody has touched is left exactly as the page has it - and a
    // page nobody has touched at all, which is most of them, walks off here
    // without looking at its sentences.
    if (translations.isEmpty) return

    val marked = remember(sentences, translations) { markedPassages(sentences, translations) }

    // Where the marks land on the page as it is drawn now, worked out here
    // rather than in the draw block below: the sweep repaints that block on
    // every frame, and none of this moves between one of those frames and the
    // next. [scale] is a key because a pinch is the one thing that does move it,
    // and the page is recomposed for that anyway.
    val bands = remember(marked, scale) {
        marked.mapNotNull { passage ->
            if (passage.state.panel() != null) return@mapNotNull null

            val style = passage.state.markStyle() ?: return@mapNotNull null

            passage.quads.map { it.asLine(scale) }
                .filter { it.width > 0f && it.height > 0f }
                .takeIf { it.isNotEmpty() }
                ?.let { PassageBand(style, it) }
        }
    }

    val markColors = passageMarkColors()

    // Read where it is drawn rather than here: read during composition it would
    // recompose the page on every frame of the sweep.
    val sweep = passageSweep(marked.any { it.state is PassageTranslation.Loading })

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            // Every mark in one pass rather than a full-page layer per
            // sentence: they all draw into the same page.
            //
            // The same soft highlight the set text is marked with, so moving
            // between a PDF and an EPUB is not moving between two readers, and
            // faint enough to leave somebody else's typesetting readable under
            // it.
            //
            // A passage with something to put on the page brings its own
            // highlight - see TranslationPanel - so it is left out of the bands
            // above; drawing one here too would leave the wider of the two
            // showing round the edges of the narrower.
            .drawBehind { drawPassageBands(bands, markColors, sweep.value) },
    ) {
        marked.forEach { passage ->
            val panel = passage.state.panel()

            // Nothing is drawn over a sentence still being translated: the
            // reader can still read the original underneath while they wait.
            AnimatedVisibility(
                visible = panel != null,
                // On the app's own curves rather than on Compose's defaults,
                // so a panel landing over a word moves the way everything else
                // that arrives and leaves in this app does - see [LukiaMotion].
                enter = fadeIn(tween(PANEL_FADE_MILLIS, easing = LukiaMotion.Standard)),
                exit = fadeOut(tween(PANEL_FADE_MILLIS, easing = LukiaMotion.Exit)),
            ) {
                // Held after it goes, so the panel fades out with words in it
                // rather than emptying first and then fading.
                val shown = panel ?: return@AnimatedVisibility

                TranslationPanel(
                    // The line rather than the letters, and the same line the
                    // highlight would have covered - so the panel sits exactly
                    // where the mark would have been instead of inside it.
                    bounds = passage.bounds.asLine(),
                    // What is left of the page to the right of it, so a
                    // translation longer than what it replaces has somewhere to
                    // go without running off the edge.
                    available = maxWidth,
                    // Measured from the shortest of the passage's lines, which is
                    // one line - not the block they add up to. See below.
                    glyphHeight = passage.quads.minOf { it.height },
                    text = shown.words(),
                    detail = shown.detail,
                    textColor = if (shown is PanelContent.Failure) {
                        colors.error
                    } else {
                        colors.onSurface
                    },
                    tint = markColors.of(
                        passage.state.markStyle() ?: PassageMarkStyle.Translated,
                    ),
                    scale = scale,
                    pageColor = pageColor,
                    // The passage it stands for, named by a point the page will
                    // recognise as inside it - which is anywhere in the first
                    // of the rectangles it covers.
                    onPointedAt = { held ->
                        onTranslationPointedAt(passage.middle.x, passage.middle.y, held)
                    },
                )
            }
        }
    }
}

/**
 * Everything on this page the reader has asked about, gathered together.
 *
 * Paragraphs, sentences and single words in one list, because what is done with
 * any of them is the same: mark where it is, and cover it once there is
 * something to put in its place. Each is only on the page while nothing larger
 * has been translated over the top of it - a paragraph asked about whole leaves
 * none of its sentences showing, and a translated sentence leaves none of its
 * words.
 */
private fun markedPassages(
    sentences: List<PageSentence>,
    translations: SentenceTranslations,
): List<MarkedPassage> = sentences.groupBy { it.blockIndex }.flatMap { (blockIndex, inBlock) ->
    val paragraph = translations.paragraph(blockIndex)

    if (paragraph == null) {
        inBlock.flatMap { it.markedPassages(translations) }
    } else {
        val quads = inBlock.flatMap { it.quads }

        listOf(MarkedPassage(quads, quads.union(), paragraph))
    }
}

/** What the reader has asked about inside one sentence, if anything. */
private fun PageSentence.markedPassages(translations: SentenceTranslations): List<MarkedPassage> {
    val whole = translations[index]

    if (whole != null) return listOf(MarkedPassage(quads, bounds, whole))

    return words.mapNotNull { word ->
        translations.words(index)[word.index]?.let {
            MarkedPassage(word.quads, word.bounds, it)
        }
    }
}

/**
 * One passage of a page the reader has asked about - a paragraph, a sentence, or
 * a single word inside one - and what has become of it.
 *
 * All three are the same thing to draw: some rectangles to mark, and somewhere
 * to put the answer once it arrives. Keeping them apart here would be three
 * copies of the same loop that differed only in where the rectangles came from.
 */
private data class MarkedPassage(
    val quads: List<TextQuad>,
    val bounds: TextQuad,
    val state: PassageTranslation,
) {

    /**
     * A point the page will recognise as inside this passage, for naming it back
     * to whoever asks - anywhere in the first of the rectangles it covers will
     * do, and the middle of that one is furthest from every edge.
     */
    val middle: Offset
        get() = quads.first().let {
            Offset(it.left + it.width / 2, it.top + it.height / 2)
        }
}

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
    bottom = bottom + height * LINE_BELOW_GLYPHS,
)

private fun TextQuad.asLine(scale: Float): Rect = asLine().let { line ->
    Rect(
        left = line.left * scale,
        top = line.top * scale,
        right = line.right * scale,
        bottom = line.bottom * scale,
    )
}
