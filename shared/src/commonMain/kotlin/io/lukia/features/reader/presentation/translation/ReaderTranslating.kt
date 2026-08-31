package io.lukia.features.reader.presentation.translation

import io.lukia.core.presentation.dataOrNull
import io.lukia.features.reader.domain.ReaderDocument
import io.lukia.features.reader.presentation.ReaderViewModel
import io.lukia.features.translate.domain.BulkTranslation
import io.lukia.features.translate.domain.settings.TargetLanguage
import io.lukia.features.translate.domain.settings.TranslationGestures
import io.lukia.features.translate.domain.text.TextAnchor
import io.lukia.features.translate.presentation.PassageSelection
import io.lukia.features.translate.presentation.PassageTranslation
import kotlinx.coroutines.flow.StateFlow

/**
 * Translating's surface on the reader, kept beside the ViewModel rather than
 * inside it.
 *
 * Every one of these hands straight on to [ReaderTranslations], which already
 * holds what has been translated and what is being translated now. Gathering the
 * pass-throughs here keeps the ViewModel about opening a book and moving through
 * it, and puts everything the reader can ask of a translation on one page.
 */
val ReaderViewModel.translations: StateFlow<Map<TextAnchor, PassageTranslation>>
    get() = translation.passages

val ReaderViewModel.bulkTranslation: StateFlow<BulkTranslation>
    get() = translation.bulkTranslation

val ReaderViewModel.translateOnScreen: StateFlow<TranslateOnScreen>
    get() = translation.onScreen

val ReaderViewModel.shownTranslations: StateFlow<ShownTranslations>
    get() = translation.shown

val ReaderViewModel.translationGestures: StateFlow<TranslationGestures>
    get() = translation.gestures

val ReaderViewModel.targetLanguage: StateFlow<TargetLanguage>
    get() = translation.targetLanguage

val ReaderViewModel.translationConfigured: StateFlow<Boolean>
    get() = translation.configured

/** A passage the reader pointed at. */
fun ReaderViewModel.onPassageSelected(selection: PassageSelection) {
    val document = uiState.value.dataOrNull ?: return

    translation.onPassageTapped(selection.anchor) { requestFor(document, selection) }
}

/**
 * Every passage the reader can see, translated in one go.
 *
 * Asked for rather than tapped, so a sentence they have already flipped back to
 * the original stays as they left it and a failure is not retried behind their
 * back - see
 * [io.lukia.features.translate.presentation.TranslationSession.onPassageRequested].
 */
fun ReaderViewModel.onPassagesRequested(selections: List<PassageSelection>) {
    val document = uiState.value.dataOrNull ?: return

    selections.forEach { selection ->
        translation.onPassageRequested(selection.anchor) {
            requestFor(document, selection)
        }
    }
}

fun ReaderViewModel.onTranslateOnScreen() {
    translation.requestOnScreen()
}

fun ReaderViewModel.setTranslationsVisible(visible: Boolean) {
    translation.showTranslations(visible)
}

fun ReaderViewModel.setContinuousTranslation(continuous: Boolean) {
    translation.setContinuous(continuous)
}

/**
 * The reader has changed what translating does from the panel over the book
 * rather than from the settings screen. Both write the same setting - see
 * [ReaderTranslations.setTargetLanguage].
 */
fun ReaderViewModel.setTargetLanguage(language: TargetLanguage) {
    translation.setTargetLanguage(language)
}

fun ReaderViewModel.setTranslationGestures(gestures: TranslationGestures) {
    translation.setGestures(gestures)
}

/** What to ask the translator for, given where the reader is in the book. */
private fun ReaderViewModel.requestFor(document: ReaderDocument, selection: PassageSelection) =
    document.translationRequest(selection, translation.currentTargetLanguage, outline.value)
