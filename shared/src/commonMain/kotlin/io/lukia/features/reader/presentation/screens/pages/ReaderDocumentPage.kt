package io.lukia.features.reader.presentation.screens.pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import io.lukia.features.reader.domain.page.PageSentence
import io.lukia.features.reader.domain.page.PageSize
import io.lukia.features.reader.presentation.components.chrome.detectReaderTaps
import io.lukia.features.reader.presentation.components.translation.PageTranslationOverlay
import io.lukia.features.reader.presentation.translation.TRANSLATE_SETTLE_MILLIS
import io.lukia.features.reader.presentation.translation.TranslateOnScreen
import io.lukia.features.translate.domain.settings.TranslationGestures
import io.lukia.features.translate.domain.text.PassageUnit
import io.lukia.features.translate.presentation.PassageSelection
import io.lukia.features.translate.presentation.SentenceTranslations
import io.lukia.shared.presentation.theme.LukiaElevation
import kotlinx.coroutines.delay

// One page of a fixed-layout document, drawn and answered for on its own - see
// ReaderPageList for the list these sit in.

/**
 * One page. The sheet is laid out at the page's own proportions before anything
 * is drawn into it, so the list's height is settled from the start and scrolling
 * never jumps as pages arrive.
 *
 * [pageFilter] is what makes the reading theme instant: switching it re-draws
 * the page that is already rendered rather than rendering it again.
 */
@Composable
internal fun DocumentPage(
    index: Int,
    pageCount: Int,
    pageSize: PageSize,
    width: Dp,
    renderWidthPx: Int,
    pageColor: Color,
    pageFilter: ColorFilter?,
    translations: SentenceTranslations,
    onToggleChrome: () -> Unit,
    onPassageSelected: (PassageSelection) -> Unit,
    onPassageHold: (PassageSelection) -> Unit,
    onPassagesRequested: (List<PassageSelection>) -> Unit,
    translateOnScreen: TranslateOnScreen,
    gestures: TranslationGestures,
    sentencesOnPage: suspend (index: Int) -> List<PageSentence>,
    renderPage: suspend (index: Int, widthPx: Int) -> ImageBitmap?,
) {
    // Read through the state rather than captured, for the same reason every
    // other lambda on this page is. These are not keys of the effects below, so
    // a caller handing down a fresh lambda would leave the running producer
    // holding the one it started with - which is harmless while both come off a
    // ViewModel and compare equal, and is a page drawn by a stale renderer the
    // first time one of them does not.
    val currentRenderPage by rememberUpdatedState(renderPage)
    val currentSentencesOnPage by rememberUpdatedState(sentencesOnPage)

    // Keyed on the width as well as the page: a resized window, or a different
    // zoom, invalidates what was drawn for the old one.
    val page by produceState<ImageBitmap?>(initialValue = null, index, renderWidthPx) {
        value = currentRenderPage(index, renderWidthPx)

        // Draw the next page while this one is being read, so arriving at it
        // does not mean waiting for it. Cancelled along with this page when it
        // scrolls away, which is what stops a fast scroll from queueing work for
        // pages already passed.
        currentRenderPage(index + 1, renderWidthPx)
    }

    // Read once per page and kept: parsing the text out of a page is far too
    // much to do again on every tap, and a reader working through a page taps
    // it several times.
    val sentences by produceState(initialValue = emptyList<PageSentence>(), index) {
        value = currentSentencesOnPage(index)
    }

    // The one number relating the file to what is on screen. Everything the
    // overlay draws is measured in the page's own units and scaled by this, so
    // a pinch or a resize moves it without recalculating anything.
    val scale = with(LocalDensity.current) { width.toPx() } / pageSize.width

    // What is on the page and how a point on it becomes a passage - see
    // PagePassages. Worked out once per page, because parsing the text out of
    // one is far too much to do again on every tap.
    val passages = remember(index, sentences) { PagePassages(index, sentences) }

    TranslateWhatIsOnThisPage(passages, translateOnScreen, onPassagesRequested)

    Box(
        modifier = Modifier
            .width(width)
            .aspectRatio(pageSize.aspectRatio)
            .shadow(elevation = LukiaElevation.page)
            .background(pageColor)
            // A page is a picture with no text behind it, so this is the only
            // thing a screen reader can be told about it. Without it the whole
            // document is silence.
            .semantics { contentDescription = "Page ${index + 1} of $pageCount" }
            .passageTaps(
                pageIndex = index,
                passages = passages,
                translations = translations,
                scale = scale,
                gestures = gestures,
                onSelect = onPassageSelected,
                onHold = onPassageHold,
                onChrome = onToggleChrome,
            ),
    ) {
        page?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                // The sheet is already at the page's proportions, so this only
                // ever scales - it never crops or letterboxes.
                contentScale = ContentScale.FillBounds,
                colorFilter = pageFilter,
                modifier = Modifier.fillMaxSize(),
            )
        }

        PageTranslationOverlay(
            sentences = sentences,
            translations = translations,
            scale = scale,
            pageColor = pageColor,
            // Already in the file's own units - the panel knows which passage it
            // covers, so nothing has to be worked back out of where it was drawn.
            // A tap puts the page's own words back; a hold on a covered word
            // opens what else it means - which on a page that is a picture can
            // only be reached here, because the panel is drawn over the word.
            onTranslationPointedAt = { x, y, held ->
                answer(
                    selection = passages.translationAt(x, y, translations),
                    held = held,
                    onSelected = onPassageSelected,
                    onHold = onPassageHold,
                )
            },
        )
    }
}

/**
 * Asks for everything written on this page, when the reader asks for what is in
 * front of them.
 *
 * A page is either on screen or it is not - there is nothing on it that can be
 * half of it - so unlike a paragraph of set text this needs nothing measured.
 * Being composed at all is close enough: the list keeps a page or two either
 * side, and translating one page ahead of somebody reading forward is what they
 * asked for anyway.
 */
@Composable
private fun TranslateWhatIsOnThisPage(
    passages: PagePassages,
    translateOnScreen: TranslateOnScreen,
    onPassagesRequested: (List<PassageSelection>) -> Unit,
) {
    val requested by rememberUpdatedState(onPassagesRequested)

    // What this page has already done. Starting at whatever is current means a
    // page composed after the reader asked stays out of that round, which is
    // right: it was not in front of them when they asked.
    var actedOn by remember { mutableStateOf(translateOnScreen.generation) }

    LaunchedEffect(translateOnScreen, passages) {
        if (passages.isEmpty) return@LaunchedEffect
        if (!translateOnScreen.continuous && translateOnScreen.generation == actedOn) {
            return@LaunchedEffect
        }

        // Cancelled along with this page when it scrolls out of the list, so a
        // page the reader went straight past is never asked about - see
        // TRANSLATE_SETTLE_MILLIS.
        if (translateOnScreen.continuous) delay(TRANSLATE_SETTLE_MILLIS)

        actedOn = translateOnScreen.generation
        requested(passages.all())
    }
}

/**
 * The gestures a page of a fixed-layout document answers.
 *
 * A PDF's page answers the same gestures as set text, down to the word and up to
 * the paragraph: the words, the sentences and the blocks they sit in are all
 * inferred back out of the glyphs and where they landed, so each of them has a
 * piece of the page of its own to be pointed at and drawn over.
 *
 * A composable extension rather than a `composed` modifier, for the reason given
 * on [Modifier.pinchToZoom]: everything here is read through
 * [rememberUpdatedState] so that one `pointerInput` survives a changing scale, a
 * changing set of translations and a caller handing down fresh lambdas.
 */
@Composable
private fun Modifier.passageTaps(
    pageIndex: Int,
    passages: PagePassages,
    translations: SentenceTranslations,
    scale: Float,
    gestures: TranslationGestures,
    onSelect: (PassageSelection) -> Unit,
    onHold: (PassageSelection) -> Unit,
    onChrome: () -> Unit,
): Modifier {
    val currentPassages by rememberUpdatedState(passages)
    val currentTranslations by rememberUpdatedState(translations)
    val currentScale by rememberUpdatedState(scale)
    val currentGestures by rememberUpdatedState(gestures)
    val currentOnSelected by rememberUpdatedState(onSelect)
    val currentOnHold by rememberUpdatedState(onHold)
    val currentOnChrome by rememberUpdatedState(onChrome)

    return pointerInput(pageIndex) {
        fun select(position: Offset, unit: PassageUnit?, held: Boolean): Boolean =
            // Back out of the drawn page into the file's own space, which is
            // where the sentences know they are.
            answer(
                selection = currentPassages.at(
                    x = position.x / currentScale,
                    y = position.y / currentScale,
                    unit = unit,
                    translations = currentTranslations,
                ),
                held = held,
                onSelected = currentOnSelected,
                onHold = currentOnHold,
            )

        detectReaderTaps(
            // A tap that reached no words - a margin, a figure, a page with no
            // text layer at all - or one the reader has set to do nothing is one
            // of the two taps that bring the chrome back.
            onTap = { position -> select(position, currentGestures.tap, held = false) },
            onLongPress = { position -> select(position, currentGestures.longPress, held = true) },
            onChrome = { currentOnChrome() },
        )
    }
}

/**
 * Hands a passage the reader pointed at to whichever of the two is meant, and
 * says whether they pointed at anything.
 *
 * A held word goes to whoever can tell whether it has been answered already,
 * which is what decides between asking about it and opening what else it means -
 * see `ReaderViewModel.onPassageHold`. Everything else is the gesture the reader
 * set it to, unchanged.
 */
private fun answer(
    selection: PassageSelection?,
    held: Boolean,
    onSelected: (PassageSelection) -> Unit,
    onHold: (PassageSelection) -> Unit,
): Boolean {
    if (selection == null) return false

    if (held && selection.unit == PassageUnit.Word) onHold(selection) else onSelected(selection)

    return true
}
