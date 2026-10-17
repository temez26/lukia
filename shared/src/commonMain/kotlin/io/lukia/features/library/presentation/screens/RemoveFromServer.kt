package io.lukia.features.library.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.presentation.bookTitle
import io.lukia.features.library.presentation.LibraryViewModel
import io.lukia.resources.Res
import io.lukia.resources.book_remove_from_server_confirm
import io.lukia.resources.book_remove_from_server_message
import io.lukia.resources.book_remove_from_server_title
import io.lukia.shared.presentation.components.surface.ConfirmDialog
import org.jetbrains.compose.resources.stringResource

/**
 * The admin's Remove from server, from the menu row to the question before it
 * happens. [ask] is null for everyone else, which is what keeps the row out of
 * their menus.
 */
@Stable
internal class ServerRemoval(private val asking: MutableState<DocumentInfo?>, canRemove: Boolean) {

    /** Asks about [DocumentInfo], a book the server has; null while the reader is not the admin. */
    val ask: ((DocumentInfo) -> Unit)? = if (canRemove) { document -> asking.value = document } else null

    /** The menu row for [document], if it has one: only the admin, only on a book the server has. */
    fun rowFor(document: DocumentInfo): (() -> Unit)? =
        ask?.takeIf { document.onServer }?.let { ask -> { ask(document) } }

    val pending: DocumentInfo? get() = asking.value

    fun dismiss() {
        asking.value = null
    }
}

@Composable
internal fun rememberServerRemoval(viewModel: LibraryViewModel): ServerRemoval {
    val canRemove by viewModel.transfers.canRemove.collectAsState()
    val asking = remember { mutableStateOf<DocumentInfo?>(null) }
    return remember(canRemove) { ServerRemoval(asking, canRemove) }
}

/** The question before a book leaves everyone's library, shown while [removal] has one pending. */
@Composable
internal fun RemoveFromServerDialog(removal: ServerRemoval, onRemove: (DocumentInfo) -> Unit) {
    val document = removal.pending ?: return

    RemoveFromServerQuestion(
        title = document.bookTitle(),
        onConfirm = { onRemove(document) },
        onDismissRequest = removal::dismiss,
    )
}

/** Also for the book's own page, which asks about the one book it shows. */
@Composable
internal fun RemoveFromServerQuestion(title: String, onConfirm: () -> Unit, onDismissRequest: () -> Unit) {
    ConfirmDialog(
        visible = true,
        title = stringResource(Res.string.book_remove_from_server_title),
        message = stringResource(Res.string.book_remove_from_server_message, title),
        confirmLabel = stringResource(Res.string.book_remove_from_server_confirm),
        onConfirm = onConfirm,
        onDismissRequest = onDismissRequest,
        destructive = true,
    )
}
