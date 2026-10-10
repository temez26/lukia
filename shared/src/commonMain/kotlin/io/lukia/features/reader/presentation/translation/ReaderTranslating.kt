package io.lukia.features.reader.presentation.translation

import io.lukia.core.domain.text.PassageUnit
import io.lukia.core.presentation.dataOrNull
import io.lukia.features.reader.domain.ReaderDocument
import io.lukia.features.reader.domain.text.TextAnchor
import io.lukia.features.reader.presentation.ReaderViewModel
import io.lukia.features.translate.domain.BulkTranslation
import io.lukia.features.translate.domain.settings.TargetLanguage
import io.lukia.features.translate.domain.settings.TranslationGestures
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

/**
 * Whether the translation service is set up, which decides how a gesture is
 * offered rather than whether it is - see the panel's gestures.
 *
 * There is no wider question any more. Both of a word's answers are web APIs
 * that need nothing set up, so a reader always has something that can translate
 * what they point at.
 */
val ReaderViewModel.translationServiceConfigured: StateFlow<Boolean>
    get() = translation.serviceConfigured

/** Whether the translator's asides are set under the paragraphs they belong to. */
val ReaderViewModel.translationNotes: StateFlow<Boolean>
    get() = translation.notes

/** The one word the reader has stopped on to ask about properly, if any. */
val ReaderViewModel.wordInquiry: StateFlow<WordInquiry?>
    get() = translation.words.inquiry

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
 * it is a word, and something has already become of it.
 *
 * A failure counts. It used to fall through and quietly ask the same service
 * again, the thing least likely to help: a word the reader's own translator
 * could not answer is exactly the one they want to take elsewhere. So it opens
 * the panel too, where the dictionary is read, the site is offered, and asking
 * again is one option rather than the only one.
 *
 * A word still arriving is the one state left out - it has nothing more to say
 * yet, and it is about to.
 */
fun ReaderViewModel.onPassageHold(selection: PassageSelection) {
    val onThePage = translation.passages.value[selection.anchor].onThePage()

    if (!translation.gestures.value.wordDetails ||
        selection.unit != PassageUnit.Word ||
        onThePage == null
    ) {
        return onPassageSelected(selection)
    }

    translation.openWordInquiry(
        selection = selection,
        onThePage = onThePage,
        // What the document said it is written in, for the sites that take a
        // language pair. The book rather than the passage: nothing here reads
        // well enough to tell a quoted line of French from the English around it.
        sourceLanguage = uiState.value.dataOrNull?.info?.metadata?.language,
    )
}

/**
 * A word the reader asked about outright, which opens what it means whether or
 * not anything has become of it yet.
 *
 * The difference from [onPassageHold] is why the two are separate. A hold is the
 * reader's own gesture with their own setting behind it, and its first job is
 * translating, so it opens this panel only on an already-answered word. This has
 * no setting and one job: it is the second mouse button, which everywhere else
 * opens the menu belonging to the thing under it rather than acting on that
 * thing first.
 *
 * Which leaves a word with nothing on the page and a panel open about it - see
 * [WordOnThePage.Untouched], where the one button offers to translate it after
 * all.
 */
fun ReaderViewModel.onPassageInquiry(selection: PassageSelection) {
    if (selection.unit != PassageUnit.Word) return onPassageSelected(selection)

    translation.openWordInquiry(
        selection = selection,
        onThePage = translation.passages.value[selection.anchor].onThePage()
            ?: WordOnThePage.Untouched,
        sourceLanguage = uiState.value.dataOrNull?.info?.metadata?.language,
    )
}

/** The reader is done with the word, and the page underneath is unchanged. */
fun ReaderViewModel.onWordInquiryDismissed() {
    translation.dismissWordInquiry()
}

/**
 * Does whatever the panel offered to do about the word on the page, and closes.
 *
 * One entry point for all three, because they are one gesture underneath: the
 * passage is put back, brought forward, or asked about again, decided by what
 * has become of it - see
 * [io.lukia.features.reader.presentation.translation.TranslationSession.onPassageActioned].
 */
fun ReaderViewModel.onInquiredWordActioned() {
    val selection = translation.inquiredWord ?: return
    val document = uiState.value.dataOrNull ?: return

    translation.onPassageActioned(selection.anchor) { requestFor(document, selection) }
    translation.dismissWordInquiry()
}

/**
 * What a passage the reader held is doing on the page, or null when it is not
 * something the panel has anything to offer about.
 */
private fun PassageTranslation?.onThePage(): WordOnThePage? = when (this) {
    is PassageTranslation.Ready ->
        if (showingOriginal) WordOnThePage.Original else WordOnThePage.Translated

    is PassageTranslation.Failed -> WordOnThePage.Failed(error)

    null, is PassageTranslation.Loading -> null
}

/**
 * Every passage the reader can see, translated in one go.
 *
 * Asked for rather than tapped, so a sentence they have already flipped back to
 * the original stays as they left it and a failure is not retried behind their
 * back - see
 * [io.lukia.features.reader.presentation.translation.TranslationSession.onPassageRequested].
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

fun ReaderViewModel.setTranslationNotes(notes: Boolean) {
    translation.setNotes(notes)
}

/** What to ask the translator for, given where the reader is in the book. */
private fun ReaderViewModel.requestFor(document: ReaderDocument, selection: PassageSelection) =
    document.translationRequest(selection, translation.currentTargetLanguage, outline.value)
