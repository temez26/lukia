package io.lukia.features.translate.infrastructure

import io.lukia.core.domain.AppError
import io.lukia.core.domain.AppErrorException

/**
 * What went wrong, told apart by what the reader would have to do about it.
 *
 * These all used to arrive as the same thing: a sentence of prose in an
 * [AppError.Unexpected], or a bare [AppError.TranslationFailed] saying only that
 * translating did not work. The words were often right and nothing above could
 * act on them, because a sentence is not something code can branch on. This is
 * the half that is, kept beside the words rather than instead of them.
 *
 * Two things read it. [RetryingTranslator] asks whether asking again could
 * possibly help - a busy service yes, a rejected key never - and the case order
 * below is that question, not a severity. The reader gets the sentence, which is
 * still written where the failure happened, because only the provider knows
 * whether the address in it is a hosted service or the reader's own laptop.
 */
internal enum class TranslationFailureKind {

    /** The key was not accepted, or has no access to what was asked for. */
    KeyRejected,

    /** The account is out of credit. Waiting never fixes this one. */
    NoCredit,

    /** Asked too often. The one failure that is nearly always over in seconds. */
    RateLimited,

    /** No such model at this server, under this name. */
    ModelNotFound,

    /** Nothing was listening, or the connection never opened. */
    Unreachable,

    /** Something is there, and it stopped answering partway. */
    TimedOut,

    /** The service is having a bad minute - a 5xx, or an overloaded 529. */
    ServiceUnavailable,

    /** More was sent than the service or the model will take. */
    TooLarge,

    /** The model declined to translate this passage. */
    Refused,

    /** The model ran out of room before it finished the answer. */
    RanOutOfRoom,

    /** The connection ended in the middle of an answer that was still arriving. */
    CutShort,

    /** Something came back and it was not a translation. */
    NotATranslation,

    /** The request was rejected on its own terms - a bad parameter, a bad shape. */
    Rejected,
}

/**
 * Whether asking the same question again could plausibly answer it.
 *
 * Deliberately narrow. Everything left out is a state that will still be true in
 * two seconds - a key that is wrong is wrong, an account with no credit has no
 * credit - and retrying those spends the reader's battery to arrive at the same
 * sentence, twice as slowly. See [RetryingTranslator] for what is done with a
 * yes.
 */
internal val TranslationFailureKind.isWorthRetrying: Boolean
    get() = when (this) {
        TranslationFailureKind.RateLimited,
        TranslationFailureKind.Unreachable,
        TranslationFailureKind.TimedOut,
        TranslationFailureKind.ServiceUnavailable,
        TranslationFailureKind.CutShort,
        -> true

        TranslationFailureKind.KeyRejected,
        TranslationFailureKind.NoCredit,
        TranslationFailureKind.ModelNotFound,
        TranslationFailureKind.TooLarge,
        TranslationFailureKind.Refused,
        TranslationFailureKind.RanOutOfRoom,
        TranslationFailureKind.NotATranslation,
        TranslationFailureKind.Rejected,
        -> false
    }

/**
 * A failed translation, in both the forms it has to be in at once.
 *
 * [AppErrorException] is what everything above this feature already understands,
 * so this is one - a screen that catches an app error goes on showing the same
 * sentence it always did. What is added is [kind], for the one caller inside the
 * feature that has a decision to make, and [retryAfterSeconds] for the service
 * that said how long to wait rather than leaving it to be guessed.
 */
internal class TranslationFailureException(
    val kind: TranslationFailureKind,
    explanation: String? = null,
    /** What the service's own `Retry-After` said, when it sent one. */
    val retryAfterSeconds: Int? = null,
) : AppErrorException(
    when (kind) {
        // The one case with no words of its own. A blank answer is not something
        // the reader can be told anything useful about, and the app already has
        // a name for it.
        TranslationFailureKind.NotATranslation ->
            explanation?.let { AppError.Unexpected(it) } ?: AppError.TranslationFailed

        else -> AppError.Unexpected(explanation)
    },
)

/** Raises [kind] with the words the reader will see. */
internal fun translationFailed(
    kind: TranslationFailureKind,
    explanation: String? = null,
    retryAfterSeconds: Int? = null,
): Nothing = throw TranslationFailureException(kind, explanation, retryAfterSeconds)
