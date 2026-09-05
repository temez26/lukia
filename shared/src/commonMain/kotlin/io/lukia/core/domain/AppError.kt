package io.lukia.core.domain

import kotlinx.coroutines.CancellationException

/**
 * Every failure the app knows how to talk about.
 *
 * Failures are modelled as data rather than as message strings so the layer that
 * raises one does not have to decide how it reads. Wording lives with the UI, in
 * `core/presentation/AppErrorDisplay.kt`.
 *
 * [Unexpected] is the catch-all. Anything that reaches a ViewModel without being
 * one of the named cases becomes one of these, which is what stops an unforeseen
 * throw from turning into a silent crash.
 */
sealed interface AppError {
    data object DocumentNotFound : AppError

    data object UnsupportedFileType : AppError

    /**
     * The library can hold this format, but the reader cannot open it yet.
     * Distinct from [UnsupportedFileType], which is about what may be imported
     * at all - telling someone their EPUB is unsupported right after the app
     * accepted it would read as a bug.
     */
    data object FormatNotReadable : AppError

    data object EmptyFolder : AppError

    /**
     * Nothing is set up to translate with yet. A configuration problem rather
     * than a failure, and the only one here the reader can fix themselves -
     * which is why it is not an [Unexpected] carrying an apology.
     */
    data object TranslationUnavailable : AppError

    /** Something is set up to translate, and it did not come back with words. */
    data object TranslationFailed : AppError

    /**
     * The catch-all, carrying words written for a reader or none at all.
     *
     * [explanation] is prose somebody wrote on purpose - "check the server is
     * running", "that passage is too long" - and never a throwable's own
     * message. The two used to share this field, and the difference matters
     * because `AppErrorDisplay` puts whatever is here on screen: a decoder's
     * message is addressed to whoever wrote the decoder, and on the desktop it
     * is routinely a file path with the reader's own name in it.
     *
     * So the field holds one kind of thing, and [toAppError] no longer fills
     * it. What is lost that way is recorded where the throwable still had a
     * stack trace, rather than carried up to a screen that cannot use it.
     */
    data class Unexpected(val explanation: String? = null) : AppError
}

/**
 * Carries an [AppError] through [Result], whose failure channel is typed to
 * [Throwable]. Not thrown across layers by hand - use [asFailure].
 *
 * Open so a feature can attach detail of its own to a failure without core
 * having to learn what that detail is. Everything here still reads it as an
 * app error and shows the same words; whoever inside the feature has a decision
 * to make catches the subclass and asks it - see
 * `features/translate/infrastructure/TranslationFailure.kt`.
 */
open class AppErrorException(val error: AppError) : Exception(error.toString())

fun AppError.asFailure(): Result<Nothing> = Result.failure(AppErrorException(this))

/**
 * Narrows any throwable to the app's own vocabulary. Everything the app raises
 * deliberately survives as itself; everything else lands in
 * [AppError.Unexpected] rather than escaping unhandled.
 *
 * Deliberately lossy for that second kind. A throwable's `message` is written
 * for whoever wrote the code that threw it, and this is the one place it could
 * reach a screen - `FileNotFoundException` says
 * `/Users/someone/Documents/...`, and Ktor puts the whole request URL in one.
 * Nothing here can tell those from the sentences the app writes for readers,
 * so it keeps neither and lets [AppError.Unexpected] speak for itself.
 *
 * The message is not the loss it looks like: by the time a throwable arrives
 * here it has crossed a `withContext` and the coroutine machinery, and the
 * frames worth having are long gone. Whatever raised it is close to something
 * that absorbed it, and that is where it is worth writing down.
 */
fun Throwable.toAppError(): AppError = when (this) {
    is AppErrorException -> error
    else -> AppError.Unexpected()
}

/**
 * Like [runCatching], but re-throws [CancellationException] instead of
 * swallowing it - a cancelled coroutine is how a scope shuts down, not a failure
 * to report, and catching it breaks structured concurrency.
 *
 * This is the boundary every ViewModel runs its work through: use cases wrap the
 * failures they anticipate in a [Result] and let the rest throw, and this is
 * what makes sure the rest still reaches the user as an [AppError.Unexpected]
 * rather than dying inside the coroutine.
 */
// The one place a Throwable is caught on purpose. This is the boundary the rule
// exists to protect - narrowing it to named types is exactly what it must not do,
// because what it is for is the failure nobody named.
@Suppress("TooGenericExceptionCaught")
inline fun <T> runCatchingApp(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: Throwable) {
    Result.failure(failure)
}
