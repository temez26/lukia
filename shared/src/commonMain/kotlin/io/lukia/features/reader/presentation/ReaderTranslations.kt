package io.lukia.features.reader.presentation

import io.lukia.features.translate.application.TranslatePassageUseCase
import io.lukia.features.translate.domain.BulkTranslation
import io.lukia.features.translate.domain.TargetLanguage
import io.lukia.features.translate.domain.TextAnchor
import io.lukia.features.translate.domain.TranslationGestures
import io.lukia.features.translate.domain.TranslationRequest
import io.lukia.features.translate.infrastructure.TranslationSettingsRepository
import io.lukia.features.translate.presentation.PassageTranslation
import io.lukia.features.translate.presentation.TranslationSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Translating while reading, for as long as one book is open.
 *
 * Split from [ReaderViewModel] because opening a book and keeping the reader's
 * place is one job and translating what is on its pages is another. What is left
 * here is everything that would still make sense if the reader never turned a
 * page: what has been translated, what the reader has asked to be offered, and
 * what a tap should do.
 *
 * It does not know what a book is. Every request is built by the caller, which
 * is the only place that can say which book the words came from.
 */
internal class ReaderTranslations(
    translatePassage: TranslatePassageUseCase,
    private val settings: TranslationSettingsRepository,
    scope: CoroutineScope,
) {

    /**
     * Every sentence translated while this book is open. Tied to the ViewModel's
     * lifetime rather than to the screen so that flipping between a translation
     * and the original survives the reader turning the device round.
     */
    private val session = TranslationSession(translatePassage, scope)

    val passages: StateFlow<Map<TextAnchor, PassageTranslation>> = session.passages

    private val _onScreen = MutableStateFlow(TranslateOnScreen.Idle)

    /**
     * Whether the reader has asked to be offered translating more of the book
     * than they pointed at.
     */
    val bulkTranslation: StateFlow<BulkTranslation> = settings.settings
        .map { it.bulkTranslation }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = settings.settings.value.bulkTranslation,
        )

    /**
     * What is standing, with anything the reader has since turned off taken out
     * of it.
     *
     * Masked here rather than guarded at every place that could set it, so that
     * turning "keep translating" off in Settings stops it at once - including
     * while it is running, with a book open, halfway down a chapter. A setting
     * that only took effect the next time it was switched on would leave the
     * expensive one running after the reader had told it to stop.
     */
    val onScreen: StateFlow<TranslateOnScreen> =
        combine(_onScreen, bulkTranslation) { standing, offered ->
            if (offered.asYouRead) standing else standing.copy(continuous = false)
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = TranslateOnScreen.Idle,
        )

    /**
     * Whether the translations in this book are the words on the page.
     *
     * Read off the passages themselves rather than kept as a flag beside them, so
     * there is only ever one answer to the question: a reader who taps a single
     * sentence back to the original has hidden one of them, and the control that
     * hides all of them says so without being told.
     */
    val shown: StateFlow<ShownTranslations> = session.passages
        .map { passages ->
            val answered = passages.values.filterIsInstance<PassageTranslation.Ready>()

            ShownTranslations(
                any = answered.isNotEmpty(),
                visible = answered.any { !it.showingOriginal },
            )
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = ShownTranslations.None,
        )

    /**
     * What a tap and a long press on the book ask for, straight from the
     * settings - so changing one takes effect on the very next tap rather than
     * the next book.
     */
    val gestures: StateFlow<TranslationGestures> = settings.settings
        .map { it.gestures }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = settings.settings.value.gestures,
        )

    /**
     * Whether there is anything set up to translate with.
     *
     * The reader needs this to decide whether to offer the controls at all: a
     * button whose only possible outcome is "nothing is set up to translate
     * with" is worse than no button, and most of a reading app's users never
     * configure one.
     */
    val configured: StateFlow<Boolean> = settings.settings
        .map { it.isConfigured }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = settings.settings.value.isConfigured,
        )

    val targetLanguage: TargetLanguage get() = settings.settings.value.targetLanguage

    /** A passage the reader pointed at. */
    fun onPassageTapped(anchor: TextAnchor, request: () -> TranslationRequest) {
        session.onPassageTapped(anchor, request)
    }

    /**
     * A passage the reader asked for rather than tapped, so one they have already
     * flipped back to the original stays as they left it and a failure is not
     * retried behind their back - see
     * [io.lukia.features.translate.presentation.TranslationSession.onPassageRequested].
     */
    fun onPassageRequested(anchor: TextAnchor, request: () -> TranslationRequest) {
        session.onPassageRequested(anchor, request)
    }

    /**
     * Asks for everything on screen once.
     *
     * A number that goes up rather than an event, because what is on screen is
     * only known where the text is laid out. Every block of it watches this and
     * answers for itself - see [TranslateOnScreen].
     */
    fun requestOnScreen() {
        if (!bulkTranslation.value.onScreen) return

        _onScreen.value = _onScreen.value.requested()
    }

    /**
     * Shows or hides every translation in the book at once.
     *
     * Hiding is how somebody reads a stretch they have already worked through in
     * the book's own words, without giving up the answers - they are still there,
     * still paid for, one press away.
     */
    fun showTranslations(visible: Boolean) {
        session.showTranslations(visible)
    }

    /**
     * Whether text arriving in view is translated as it arrives.
     *
     * Not stored with the reader's other settings, and deliberately: this one
     * spends money for as long as it is on, and a setting that survived closing
     * the book would go on spending it in the next one without being asked for
     * again.
     */
    fun setContinuous(continuous: Boolean) {
        if (continuous && !bulkTranslation.value.asYouRead) return

        _onScreen.value = _onScreen.value.copy(continuous = continuous)
    }

    /**
     * Forgets this book's translations. The anchors point into the document being
     * thrown away, and the one opened in its place may not have a sentence where
     * each of them says; a standing order to translate what is on screen belongs
     * to the book that was open, not to whatever opens next.
     */
    fun clear() {
        session.clear()
        _onScreen.value = TranslateOnScreen.Idle
    }
}
