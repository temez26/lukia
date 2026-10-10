package io.lukia.features.reader.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import io.lukia.features.reader.presentation.ReaderViewModel
import io.lukia.features.reader.presentation.components.chrome.readerToolbarHeight
import io.lukia.features.reader.presentation.screens.translation.ReaderTranslating
import io.lukia.features.reader.presentation.screens.translation.ReaderTranslatingActions
import io.lukia.features.reader.presentation.translation.bulkTranslation
import io.lukia.features.reader.presentation.translation.onInquiredWordActioned
import io.lukia.features.reader.presentation.translation.onPassageHold
import io.lukia.features.reader.presentation.translation.onPassageInquiry
import io.lukia.features.reader.presentation.translation.onPassageSelected
import io.lukia.features.reader.presentation.translation.onPassagesRequested
import io.lukia.features.reader.presentation.translation.onTranslateOnScreen
import io.lukia.features.reader.presentation.translation.onWordInquiryDismissed
import io.lukia.features.reader.presentation.translation.setContinuousTranslation
import io.lukia.features.reader.presentation.translation.setTargetLanguage
import io.lukia.features.reader.presentation.translation.setTranslationGestures
import io.lukia.features.reader.presentation.translation.setTranslationNotes
import io.lukia.features.reader.presentation.translation.setTranslationsVisible
import io.lukia.features.reader.presentation.translation.shownTranslations
import io.lukia.features.reader.presentation.translation.targetLanguage
import io.lukia.features.reader.presentation.translation.translateOnScreen
import io.lukia.features.reader.presentation.translation.translationGestures
import io.lukia.features.reader.presentation.translation.translationNotes
import io.lukia.features.reader.presentation.translation.translationServiceConfigured
import io.lukia.features.reader.presentation.translation.translations
import io.lukia.features.reader.presentation.translation.wordInquiry
import io.lukia.shared.presentation.theme.Space
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

internal val ChromeVerticalPadding: Dp = Space.md

/**
 * What the floating toolbar occupies, and so what the first page must clear.
 *
 * Derived from the bar rather than fixed, because the bar's own height follows
 * whether the window is being pointed at or touched.
 */
@Composable
internal fun chromeHeight(): Dp = readerToolbarHeight() + ChromeVerticalPadding * 2

/** The margin a page's text is set inside its sheet. */
internal val SheetPadding: Dp = 20.dp

@Composable
fun ReaderScreen(
    location: String,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = koinViewModel(parameters = { parametersOf(location) }),
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val outline by viewModel.outline.collectAsState()
    val initialPosition by viewModel.initialPosition.collectAsState()

    ReportReaderVisibility(viewModel::onScreenChanged)

    ReaderContent(
        uiState = uiState,
        settings = settings,
        outline = outline,
        initialPosition = initialPosition,
        translating = readerTranslating(viewModel),
        translatingActions = remember(viewModel) { viewModel.translatingActions() },
        layout = readerLayout(viewModel),
        onBack = onBack,
        onRetry = viewModel::retry,
        onSettingsChange = viewModel::updateSettings,
        onPageSettled = viewModel::onPageSettled,
        sentencesOnPage = viewModel::sentencesOn,
        renderPage = viewModel::page,
    )
}

/**
 * Tells [onVisibleChange] whether the book is actually in front of somebody,
 * which is what the reading clock counts - see `ReadingTimeRecorder.pause`.
 *
 * Started and stopped rather than resumed and paused. A reader who pulls the
 * notification shade over the book, or puts a dialog in front of it, has not
 * stopped reading, and a clock that stopped for either would undercount exactly
 * the careful reader this app is for. Going behind another app is the line worth
 * drawing, and that is what stopping means.
 *
 * The lambda is read through [rememberUpdatedState] rather than keyed into the
 * effect, so a caller handing down a fresh one each composition does not
 * re-register the observer - which would report the book as arriving again every
 * time anything on the screen changed.
 */
@Composable
private fun ReportReaderVisibility(onVisibleChange: (Boolean) -> Unit) {
    val report by rememberUpdatedState(onVisibleChange)

    LifecycleStartEffect(Unit) {
        report(true)

        onStopOrDispose { report(false) }
    }
}

/**
 * How this book is being laid out, and what became of asking for it as text.
 *
 * Gathered here rather than beside the twenty other things the screen collects,
 * because all six of them are about one question and only [ReaderLayoutUi] ever
 * asks it - so reading them where the screen reads everything else buries what
 * the screen is doing.
 */
@Composable
private fun readerLayout(viewModel: ReaderViewModel): ReaderLayoutUi {
    val pdfLayout by viewModel.pdfLayout.collectAsState()
    val layoutOffered by viewModel.layoutOffered.collectAsState()
    val textExtraction by viewModel.textExtraction.collectAsState()
    val noTextLayer by viewModel.noTextLayer.collectAsState()

    return ReaderLayoutUi(
        layout = pdfLayout,
        offered = layoutOffered,
        converting = textExtraction,
        noTextLayer = noTextLayer,
        onLayoutChange = viewModel::setPdfLayout,
        onNoticeDismiss = viewModel::dismissNoTextLayerNotice,
    )
}

@Composable
private fun readerTranslating(viewModel: ReaderViewModel): ReaderTranslating {
    val passages by viewModel.translations.collectAsState()
    val onScreen by viewModel.translateOnScreen.collectAsState()
    val serviceConfigured by viewModel.translationServiceConfigured.collectAsState()
    val shown by viewModel.shownTranslations.collectAsState()
    val gestures by viewModel.translationGestures.collectAsState()
    val targetLanguage by viewModel.targetLanguage.collectAsState()
    val offered by viewModel.bulkTranslation.collectAsState()
    val wordInquiry by viewModel.wordInquiry.collectAsState()
    val notes by viewModel.translationNotes.collectAsState()

    return ReaderTranslating(
        passages = passages,
        onScreen = onScreen,
        serviceConfigured = serviceConfigured,
        shown = shown,
        gestures = gestures,
        targetLanguage = targetLanguage,
        offered = offered,
        wordInquiry = wordInquiry,
        notes = notes,
    )
}

private fun ReaderViewModel.translatingActions() = ReaderTranslatingActions(
    onPassageSelected = this::onPassageSelected,
    onPassageHold = this::onPassageHold,
    onPassageInquiry = this::onPassageInquiry,
    onPassagesRequested = this::onPassagesRequested,
    onWordInquiryDismiss = this::onWordInquiryDismissed,
    onInquiredWordAction = this::onInquiredWordActioned,
    onTranslateOnScreen = this::onTranslateOnScreen,
    onContinuousChange = this::setContinuousTranslation,
    onVisibleChange = this::setTranslationsVisible,
    onTargetLanguageChange = this::setTargetLanguage,
    onGesturesChange = this::setTranslationGestures,
    onNotesChange = this::setTranslationNotes,
)
