package io.lukia.core.presentation

import io.lukia.core.domain.AppError

/**
 * What a screen is showing: still working, showing [data], or unable to show
 * anything because of [AppError].
 *
 * Every ViewModel that loads something exposes one, so every screen renders the
 * same three states through `UiStateContent` rather than inventing its own
 * spinner and its own empty-looking failure.
 *
 * Not every failure belongs here: this is for a screen with nothing left to
 * show. One the reader can carry on past - a single file of a folder import
 * failing - is a transient message instead.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>

    data class Success<out T>(val data: T) : UiState<T>

    data class Failure(val error: AppError) : UiState<Nothing>
}

/**
 * The loaded value, or null while loading or failed. For the cases that need to
 * read through the state without branching on it - chrome that is only enabled
 * once content exists.
 */
val <T> UiState<T>.dataOrNull: T?
    get() = (this as? UiState.Success)?.data
