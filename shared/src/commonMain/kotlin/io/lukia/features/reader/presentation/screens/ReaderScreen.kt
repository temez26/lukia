package io.lukia.features.reader.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.reader.presentation.ReaderViewModel
import io.lukia.features.reader.presentation.components.chrome.readerToolbarHeight
import io.lukia.features.reader.presentation.translation.bulkTranslation
import io.lukia.features.reader.presentation.translation.onInquiredWordActioned
import io.lukia.features.reader.presentation.translation.onPassageHold
import io.lukia.features.reader.presentation.translation.onPassageSelected
import io.lukia.features.reader.presentation.translation.onPassagesRequested
import io.lukia.features.reader.presentation.translation.onTranslateOnScreen
import io.lukia.features.reader.presentation.translation.onWordInquiryDismissed
import io.lukia.features.reader.presentation.translation.setContinuousTranslation
import io.lukia.features.reader.presentation.translation.setTargetLanguage
import io.lukia.features.reader.presentation.translation.setTranslationGestures
import io.lukia.features.reader.presentation.translation.setTranslationsVisible
import io.lukia.features.reader.presentation.translation.shownTranslations
import io.lukia.features.reader.presentation.translation.targetLanguage
import io.lukia.features.reader.presentation.translation.translateOnScreen
import io.lukia.features.reader.presentation.translation.translationConfigured
import io.lukia.features.reader.presentation.translation.translationGestures
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
    val translations by viewModel.translations.collectAsState()
    val translateOnScreen by viewModel.translateOnScreen.collectAsState()
    val translationConfigured by viewModel.translationConfigured.collectAsState()
    val shownTranslations by viewModel.shownTranslations.collectAsState()
    val gestures by viewModel.translationGestures.collectAsState()
    val targetLanguage by viewModel.targetLanguage.collectAsState()
    val bulkTranslation by viewModel.bulkTranslation.collectAsState()
    val wordInquiry by viewModel.wordInquiry.collectAsState()
    val pdfLayout by viewModel.pdfLayout.collectAsState()
    val layoutOffered by viewModel.layoutOffered.collectAsState()
    val textExtraction by viewModel.textExtraction.collectAsState()
    val noTextLayer by viewModel.noTextLayer.collectAsState()

    ReaderContent(
        uiState = uiState,
        settings = settings,
        outline = outline,
        initialPosition = initialPosition,
        translations = translations,
        translateOnScreen = translateOnScreen,
        translationConfigured = translationConfigured,
        shownTranslations = shownTranslations,
        gestures = gestures,
        targetLanguage = targetLanguage,
        bulkTranslation = bulkTranslation,
        wordInquiry = wordInquiry,
        layout = ReaderLayoutUi(
            layout = pdfLayout,
            offered = layoutOffered,
            converting = textExtraction,
            noTextLayer = noTextLayer,
            onLayoutChange = viewModel::setPdfLayout,
            onNoticeDismiss = viewModel::dismissNoTextLayerNotice,
        ),
        onBack = onBack,
        onRetry = viewModel::retry,
        onSettingsChange = viewModel::updateSettings,
        onPageSettled = viewModel::onPageSettled,
        onPassageSelected = viewModel::onPassageSelected,
        onPassageHold = viewModel::onPassageHold,
        onPassagesRequested = viewModel::onPassagesRequested,
        onWordInquiryDismiss = viewModel::onWordInquiryDismissed,
        onInquiredWordAction = viewModel::onInquiredWordActioned,
        onTranslateOnScreen = viewModel::onTranslateOnScreen,
        onContinuousTranslationChange = viewModel::setContinuousTranslation,
        onTranslationsVisibleChange = viewModel::setTranslationsVisible,
        onTargetLanguageChange = viewModel::setTargetLanguage,
        onTranslationGesturesChange = viewModel::setTranslationGestures,
        sentencesOnPage = viewModel::sentencesOn,
        renderPage = viewModel::page,
    )
}
