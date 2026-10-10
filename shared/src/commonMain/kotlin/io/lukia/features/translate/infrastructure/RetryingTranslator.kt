package io.lukia.features.translate.infrastructure

import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.domain.TranslationRequest
import io.lukia.features.translate.domain.Translator
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * How many times one passage is asked for before the reader is told it failed.
 *
 * Three, because the failures this exists for are momentary: a rate limit
 * measured in seconds, a 529 while a service sheds load, a connection dropped by
 * a phone changing from wifi to cellular mid-sentence. Two more attempts covers
 * every one of those. A fourth would only be spending somebody's battery to
 * arrive at the same sentence later.
 */
private const val MAX_ATTEMPTS = 3

/** The wait before the second attempt. Doubled for each one after it. */
private val FIRST_BACKOFF = 400.milliseconds

/**
 * What no backoff of this app's own devising goes past.
 *
 * The reader is holding a phone with a finger on a sentence. A wait long enough
 * to be worth measuring is a wait they would rather have spent being told what
 * went wrong, so the ceiling is set by their patience rather than by what would
 * be politest to the service.
 */
private val MAX_BACKOFF = 4.seconds

/**
 * The longest wait this will accept from a service that named one.
 *
 * A `Retry-After` is obeyed rather than argued with - only the service knows
 * when its limit resets - but only up to here. Past this the honest thing is to
 * stop and say so: a sentence arriving forty seconds after the tap was given up
 * on thirty seconds ago, and holding the request open blocks the two that
 * [io.lukia.features.reader.presentation.translation.TranslationSession] allows.
 */
private val MAX_HONOURED_RETRY_AFTER = 10.seconds

/**
 * A translator that gives a momentary failure a second chance.
 *
 * Nothing here used to retry. Every failure was final at the first attempt,
 * which for a network-facing feature means the reader sees a rate limit that had
 * already expired, a 529 from a service shedding load for two seconds, and a
 * connection dropped as a phone changed networks - all reported as though
 * translating were broken.
 *
 * What is retried is decided by type rather than guessed from a message - see
 * [TranslationFailureKind.isWorthRetrying]. A rejected key, an account with no
 * credit, a missing model and a passage the model declined will all still be
 * true in a second, so each fails at once and sends the reader to fix the thing
 * that is actually wrong.
 *
 * ### Where this sits, and why
 *
 * Below the cache and below the coalescer:
 *
 *     Coalescing -> Caching -> Retrying -> the provider
 *
 * Below the cache so a retried success is stored once and a remembered passage
 * never retries. Below the coalescer so a page whose running header appears
 * eight times is one attempt sequence rather than eight - a retry storm against
 * a service that has just asked to be left alone is the wrong answer to a rate
 * limit.
 *
 * Re-asking is safe because there is nothing to be idempotent about: a request
 * creates nothing at the far end and reads nothing that changes. It does cost
 * tokens, which is the other reason for the ceiling on attempts.
 */
internal class RetryingTranslator(
    private val translator: Translator,
    private val attempts: Int = MAX_ATTEMPTS,
    /** Injected so tests do not spend the backoff they are asserting about. */
    private val wait: suspend (Duration) -> Unit = { delay(it) },
    /** Injected for the same reason: a test asserts about a decided wait. */
    private val jitter: () -> Double = { Random.nextDouble() },
) : Translator {

    override suspend fun translate(
        request: TranslationRequest,
        onPartial: (String) -> Unit,
    ): Translation {
        var attempt = 1

        while (true) {
            // Whether this attempt put anything on the page. A second attempt
            // starts its answer from nothing, so retrying after the reader has
            // watched half a sentence appear would take those words back and
            // write them again - and [Translator] promises the opposite, that
            // what is shown is only ever a prefix of what is coming.
            var shown = false

            try {
                return translator.translate(request) { partial ->
                    shown = true
                    onPartial(partial)
                }
            } catch (failure: TranslationFailureException) {
                val backoff = failure.backoffFor(attempt)

                if (shown || backoff == null || attempt >= attempts) throw failure

                attempt++
                wait(backoff)
            }
        }
    }

    /**
     * How long to wait before asking again, or null when asking again is not
     * worth doing.
     *
     * The service's own answer wins when it gave one, being the only party that
     * knows when its limit resets. Otherwise the wait doubles per attempt and is
     * cut by a random fraction: the jitter keeps a screenful of passages that
     * failed together from coming back together, which renews the rate limit.
     */
    private fun TranslationFailureException.backoffFor(attempt: Int): Duration? {
        if (!kind.isWorthRetrying) return null

        val asked = retryAfterSeconds?.seconds

        if (asked != null) return asked.takeIf { it <= MAX_HONOURED_RETRY_AFTER }

        val ceiling = minOf(FIRST_BACKOFF * (1 shl (attempt - 1)), MAX_BACKOFF)

        return ceiling * jitter().coerceIn(JITTER_FLOOR, 1.0)
    }

    private companion object {
        /**
         * How much of the backoff is never given up. Full jitter can draw a wait
         * of nearly nothing, which against a service that has just refused the
         * request is not a retry so much as a second refusal.
         */
        const val JITTER_FLOOR = 0.5
    }
}
