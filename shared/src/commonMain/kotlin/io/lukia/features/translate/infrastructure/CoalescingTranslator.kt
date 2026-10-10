package io.lukia.features.translate.infrastructure

import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.domain.TranslationRequest
import io.lukia.features.translate.domain.Translator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A translator that asks the same question only once at a time.
 *
 * Two taps on the same words are two requests to the reader and one to the wire:
 * a repeated line of dialogue, a running header on every page of a PDF, the same
 * sentence pointed at twice. Whoever asks second waits on the answer the first
 * is getting rather than paying for the same tokens twice.
 *
 * Only concurrent asks. Once an answer is in, [CachingTranslator] behind this
 * stops it being asked again - and remembers across the app being closed, which
 * nothing held in memory can.
 */
class CoalescingTranslator(
    private val translator: Translator,
) : Translator {

    private val guard = Mutex()
    private val inFlight = mutableMapOf<TranslationRequest, CompletableDeferred<Translation>>()

    /**
     * [onPartial] belongs to whoever opened the connection. Somebody who joined
     * a request already in flight waits for the finished answer rather than
     * being shown the middle of one they did not start - which is the honest
     * thing to show for a sentence that was already halfway translated before
     * they asked about it.
     */
    // Caught to hand the failure to whoever joined this request and rethrown
    // immediately, so nothing is absorbed here - narrowing it would leave a
    // follower waiting forever on the one failure that was not named.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun translate(
        request: TranslationRequest,
        onPartial: (String) -> Unit,
    ): Translation {
        val answer = CompletableDeferred<Translation>()

        // The request is the identity: the same words, wanted in the same
        // language, out of the same book. Anything else about them differing
        // would be a different answer.
        val joined = guard.withLock { inFlight.getOrPut(request) { answer } }

        if (joined !== answer) return joined.await()

        try {
            return translator.translate(request, onPartial).also { answer.complete(it) }
        } catch (failure: Throwable) {
            // Everyone waiting on this fails with it, including on cancellation
            // - a follower left waiting on an answer nobody is fetching any more
            // would wait for the rest of the session.
            answer.completeExceptionally(failure)
            throw failure
        } finally {
            // Uncancellable because this runs while unwinding a cancellation as
            // well, and a suspend that throws here would leave the request
            // marked as in flight forever.
            withContext(NonCancellable) { guard.withLock { inFlight.remove(request) } }
        }
    }
}
