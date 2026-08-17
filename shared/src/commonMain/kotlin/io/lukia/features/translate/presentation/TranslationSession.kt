package io.lukia.features.translate.presentation

import io.lukia.core.domain.toAppError
import io.lukia.features.translate.application.TranslatePassageUseCase
import io.lukia.features.translate.domain.TextAnchor
import io.lukia.features.translate.domain.TranslationRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
 * Translations are kept for as long as the book is open. A reader comparing two
 * renderings of the same line flips between them for free, and a chapter read
 * twice is not paid for twice.
 */
class TranslationSession(
    private val translate: TranslatePassageUseCase,
    private val scope: CoroutineScope
) {

    private val _passages = MutableStateFlow<Map<TextAnchor, PassageTranslation>>(emptyMap())
    val passages: StateFlow<Map<TextAnchor, PassageTranslation>> = _passages.asStateFlow()

    /**
     * [request] is built by the caller because only the reader knows which book
     * the sentence came out of - and is passed by name so that a tap which
     * merely flips an already-translated passage never builds one.
     */
    fun onPassageTapped(anchor: TextAnchor, request: () -> TranslationRequest) {
        when (val current = _passages.value[anchor]) {
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
        _passages.value = emptyMap()
    }

    private fun put(anchor: TextAnchor, state: PassageTranslation) {
        _passages.update { it + (anchor to state) }
    }
}
