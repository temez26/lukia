package io.lukia.core.presentation

import androidx.compose.runtime.Composable
import io.lukia.core.domain.AppError
import io.lukia.resources.Res
import io.lukia.resources.error_document_not_found_message
import io.lukia.resources.error_document_not_found_title
import io.lukia.resources.error_empty_folder_message
import io.lukia.resources.error_empty_folder_title
import io.lukia.resources.error_format_not_readable_message
import io.lukia.resources.error_format_not_readable_title
import io.lukia.resources.error_translation_failed_message
import io.lukia.resources.error_translation_failed_title
import io.lukia.resources.error_translation_unavailable_message
import io.lukia.resources.error_translation_unavailable_title
import io.lukia.resources.error_unexpected_message
import io.lukia.resources.error_unexpected_title
import io.lukia.resources.error_unsupported_file_message
import io.lukia.resources.error_unsupported_file_title
import org.jetbrains.compose.resources.stringResource

/**
 * The single place an [AppError] turns into words, following the same pattern as
 * `DocumentDisplay.kt`. Keeping it here rather than at each call site is what
 * makes the same failure read the same way in a snackbar and in a full error
 * state.
 *
 * Composable, because reading a string resource takes one. Nothing below a
 * composition may call these: a view model resolving a failure to a sentence
 * would be choosing the language for a screen it cannot see.
 */
@Composable
fun AppError.displayTitle(): String = when (this) {
    AppError.DocumentNotFound -> stringResource(Res.string.error_document_not_found_title)
    AppError.UnsupportedFileType -> stringResource(Res.string.error_unsupported_file_title)
    AppError.FormatNotReadable -> stringResource(Res.string.error_format_not_readable_title)
    AppError.EmptyFolder -> stringResource(Res.string.error_empty_folder_title)
    AppError.TranslationUnavailable -> stringResource(Res.string.error_translation_unavailable_title)
    AppError.TranslationFailed -> stringResource(Res.string.error_translation_failed_title)
    is AppError.Unexpected -> stringResource(Res.string.error_unexpected_title)
}

@Composable
fun AppError.displayMessage(): String = when (this) {
    AppError.DocumentNotFound -> stringResource(Res.string.error_document_not_found_message)
    AppError.UnsupportedFileType -> stringResource(Res.string.error_unsupported_file_message)
    AppError.FormatNotReadable -> stringResource(Res.string.error_format_not_readable_message)
    AppError.EmptyFolder -> stringResource(Res.string.error_empty_folder_message)
    AppError.TranslationUnavailable ->
        stringResource(Res.string.error_translation_unavailable_message)

    AppError.TranslationFailed -> stringResource(Res.string.error_translation_failed_message)
    // Shown as written, because everything that reaches this field was written
    // to be read - see `AppError.Unexpected`. A failure nobody foresaw arrives
    // with nothing here and says so plainly, rather than handing the reader a
    // decoder's own words.
    is AppError.Unexpected -> explanation ?: stringResource(Res.string.error_unexpected_message)
}

/**
 * One-line form, for the snackbar. The title alone is too vague to act on and
 * the pair is too long for a transient strip.
 */
@Composable
fun AppError.displayShortMessage(): String = when (this) {
    is AppError.Unexpected -> explanation ?: displayTitle()
    else -> displayMessage()
}
