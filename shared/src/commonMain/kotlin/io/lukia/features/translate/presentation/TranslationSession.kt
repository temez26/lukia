package io.lukia.features.translate.presentation

import io.lukia.core.domain.toAppError
import io.lukia.features.translate.application.TranslatePassageUseCase
import io.lukia.features.translate.domain.TextAnchor
import io.lukia.features.translate.domain.TranslationRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * How many translated passages one open book keeps.
 *
 * Far more than a reader gets through in a sitting, and small enough that the
 * map is cheap to copy - which it is on every single state change, because the
 * whole screen reads it. Without a ceiling, a long evening with a long book
 * turns every tap into a copy of every translation made since it was opened.
 *
 * What falls out is the least recently touched, which is the part of the book
 * furthest behind the reader. Tapping it again asks for it again - and by then
 * the answer is in the stored cache, so asking costs nothing.
 */
private const val MAX_KEPT_PASSAGES = 400

/**
 * Every passage translated while one book is open, and what a tap on each of
 * them now means.
 *
 * A tap is one gesture with three meanings, decided by what the passage is
 * already showing: an untouched sentence is translated, a translated one flips
 * back to the original, and one showing the original flips forward again. That
 * is the whole of "back and forth" - and holding it here rather than in the
 * screen is what makes it something that can be tested without composing
 * anything.
 *
 * Translations are kept for as long as the book is open, up to
 * [MAX_KEPT_PASSAGES] of them. A reader comparing two renderings of the same
 * line flips between them for free, and a chapter read twice is not paid for
 * twice - the part that survives closing the book is
 * [io.lukia.features.translate.infrastructure.CachingTranslator]'s doing rather
 * than this one's.
 */
class TranslationSession(
    private val translate: TranslatePassageUseCase,
    private val scope: CoroutineScope
) {

    /**
     * What is on screen, and the order it was last touched in - which is what
     * decides who is forgotten first once [MAX_KEPT_PASSAGES] is reached.
     *
     * Only ever read and written from whatever thread the taps arrive on, which
     * is the one the reader's [scope] runs on. The flow beside it is what
     * everything else sees.
     */
    private val kept = LinkedHashMap<TextAnchor, PassageTranslation>()

    private val _passages = MutableStateFlow<Map<TextAnchor, PassageTranslation>>(emptyMap())
    val passages: StateFlow<Map<TextAnchor, PassageTranslation>> = _passages.asStateFlow()

    /**
     * [request] is built by the caller because only the reader knows which book
     * the sentence came out of - and is passed by name so that a tap which
     * merely flips an already-translated passage never builds one.
     */
    fun onPassageTapped(anchor: TextAnchor, request: () -> TranslationRequest) {
        when (val current = kept[anchor]) {
            // Tapping again while it is still coming back is not a second
            // request. It is somebody wondering whether the first one landed.
            PassageTranslation.Loading -> Unit

            is PassageTranslation.Ready -> put(
                anchor,
                current.copy(showingOriginal = !current.showingOriginal)
            )

            // A failure is not a state worth keeping. Tapping it is how the
            // reader retries, so it behaves exactly as the first tap did.
            null, is PassageTranslation.Failed -> fetch(anchor, request())
        }
    }

    private fun fetch(anchor: TextAnchor, request: TranslationRequest) {
        put(anchor, PassageTranslation.Loading)

        scope.launch {
            val state = translate(request).fold(
                onSuccess = { PassageTranslation.Ready(it) },
                onFailure = { PassageTranslation.Failed(it.toAppError()) }
            )

            put(anchor, state)
        }
    }

    /**
     * Forgets everything translated so far - for when the book underneath is
     * replaced, and the anchors that pointed into it no longer point anywhere.
     */
    fun clear() {
        kept.clear()
        _passages.value = emptyMap()
    }

    private fun put(anchor: TextAnchor, state: PassageTranslation) {
        // Removed before it is added so that touching a passage moves it to the
        // end, which is what makes forgetting least-recently-used rather than
        // oldest-first.
        kept.remove(anchor)
        kept[anchor] = state

        while (kept.size > MAX_KEPT_PASSAGES) kept.remove(kept.keys.first())

        _passages.value = kept.toMap()
    }
}
