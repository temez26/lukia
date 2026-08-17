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

    data class Unexpected(val cause: String? = null) : AppError
}

/**
 * Carries an [AppError] through [Result], whose failure channel is typed to
 * [Throwable]. Not thrown across layers by hand - use [asFailure].
 */
class AppErrorException(val error: AppError) : Exception(error.toString())

fun AppError.asFailure(): Result<Nothing> = Result.failure(AppErrorException(this))

/**
 * Narrows any throwable to the app's own vocabulary. Everything the app raises
 * deliberately survives as itself; everything else lands in
 * [AppError.Unexpected] rather than escaping unhandled.
 */
fun Throwable.toAppError(): AppError = when (this) {
    is AppErrorException -> error
    else -> AppError.Unexpected(message)
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
inline fun <T> runCatchingApp(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: Throwable) {
    Result.failure(failure)
}
