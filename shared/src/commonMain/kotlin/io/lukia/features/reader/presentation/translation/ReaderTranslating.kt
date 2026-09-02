package io.lukia.features.reader.presentation.translation

import io.lukia.core.presentation.dataOrNull
import io.lukia.features.reader.domain.ReaderDocument
import io.lukia.features.reader.presentation.ReaderViewModel
import io.lukia.features.translate.domain.BulkTranslation
import io.lukia.features.translate.domain.settings.TargetLanguage
import io.lukia.features.translate.domain.settings.TranslationGestures
import io.lukia.features.translate.domain.text.PassageUnit
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

/** The one word the reader has stopped on to ask about properly, if any. */
val ReaderViewModel.wordInquiry: StateFlow<WordInquiry?>
    get() = translation.words.inquiry

/** A passage the reader pointed at. */
fun ReaderViewModel.onPassageSelected(selection: PassageSelection) {
    val document = uiState.value.dataOrNull ?: return

    translation.onPassageTapped(selection.anchor) { requestFor(document, selection) }
}

/**
 * A passage the reader held rather than tapped.
 *
 * A hold on a word that has already been answered opens what else that word
 * means; a hold on anything else is the gesture the reader set it to, unchanged.
 * The split is here rather than in the page, because whether a word has an
 * answer is something the session knows and the text on screen does not.
 *
 * Three things have to hold, and everything else falls through to the gesture:
 * the reader has asked to be offered this at all - see
 * [io.lukia.features.translate.domain.settings.TranslationGestures.wordDetails] -
 * it is a word, and it has an answer already. A word still arriving has nothing
 * more to say yet, and one that failed is retried by the hold exactly as it
 * always was.
 */
fun ReaderViewModel.onPassageHold(selection: PassageSelection) {
    val answered = translation.passages.value[selection.anchor]

    if (!translation.gestures.value.wordDetails ||
        selection.unit != PassageUnit.Word ||
        answered !is PassageTranslation.Ready
    ) {
        return onPassageSelected(selection)
    }

    translation.openWordInquiry(
        anchor = selection.anchor,
        word = selection.passage,
        showingOriginal = answered.showingOriginal,
        // What the document said it is written in, for the sites that take a
        // language pair. The book rather than the passage: nothing here reads
        // well enough to tell a quoted line of French from the English around it.
        sourceLanguage = uiState.value.dataOrNull?.info?.metadata?.language,
    )
}

/** The reader is done with the word, and the page underneath is unchanged. */
fun ReaderViewModel.onWordInquiryDismissed() {
    translation.words.dismiss()
}

/**
 * Puts the book's own word back, or the translation over it again - which is
 * what a second hold on the word used to do before a hold opened the panel this
 * is a button in.
 */
fun ReaderViewModel.onInquiredWordFlipped() {
    translation.flipInquiredWord()
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
