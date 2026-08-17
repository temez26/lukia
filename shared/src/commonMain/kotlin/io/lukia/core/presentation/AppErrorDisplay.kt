package io.lukia.core.presentation

import io.lukia.core.domain.AppError

/**
 * The single place an [AppError] turns into words, following the same pattern as
 * `DocumentDisplay.kt`. Keeping it here rather than at each call site is what
 * makes the same failure read the same way in a snackbar and in a full error
 * state.
 */
fun AppError.displayTitle(): String = when (this) {
    AppError.DocumentNotFound -> "Document not found"
    AppError.UnsupportedFileType -> "Unsupported file"
    AppError.FormatNotReadable -> "Not readable yet"
    AppError.EmptyFolder -> "Nothing to import"
    AppError.TranslationUnavailable -> "Translation is not set up"
    AppError.TranslationFailed -> "Could not translate that"
    is AppError.Unexpected -> "Something went wrong"
}

fun AppError.displayMessage(): String = when (this) {
    AppError.DocumentNotFound -> "It may have been moved or removed from your library."
    AppError.UnsupportedFileType -> "Lukia can open PDF and EPUB files."
    AppError.FormatNotReadable -> "Reading EPUB files is not ready yet - PDFs open today."
    AppError.EmptyFolder -> "No PDF or EPUB files found in this folder."
    AppError.TranslationUnavailable ->
        "Choose a language to translate into in Settings, and add a translation service."
    AppError.TranslationFailed -> "The translation service did not answer. Try again."
    // The underlying message is worth showing when there is one: for an
    // unforeseen failure it is the only clue either the user or a bug report
    // will have.
    is AppError.Unexpected -> cause ?: "An unexpected error occurred."
}

/**
 * One-line form, for the snackbar. The title alone is too vague to act on and
 * the pair is too long for a transient strip.
 */
fun AppError.displayShortMessage(): String = when (this) {
    is AppError.Unexpected -> cause ?: displayTitle()
    else -> displayMessage()
}
