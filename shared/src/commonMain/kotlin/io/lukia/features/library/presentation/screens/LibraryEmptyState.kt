package io.lukia.features.library.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.library.presentation.ImportProgress
import io.lukia.features.library.presentation.components.ImportBookCard
import io.lukia.shared.presentation.components.EmptyState
import io.lukia.shared.presentation.components.LaunchReveal
import io.lukia.shared.presentation.components.launchReveal
import io.lukia.shared.presentation.theme.LocalLukiaDimens

/** The lone import card on an empty screen, at about a grid cell's width. */
private val EmptyStateCoverWidth: Dp = 116.dp

// What a library with nothing in it looks like, and what it says while it is
// being filled.

/**
 * What the screen says while it is filling an empty library.
 *
 * The count is only ever the one the ViewModel offers, so a single file reads as
 * plain "Importing books…" rather than as a progress bar that is finished before
 * it is seen.
 */
internal fun importingMessage(progress: ImportProgress?): String =
    if (progress == null) {
        "Importing books…"
    } else {
        "Importing ${progress.completed} of ${progress.total}…"
    }

/** What a successfully read library is showing. */
internal enum class LibraryContentState {
    Importing,
    Empty,
    Content,
}

@Composable
internal fun LibraryEmptyState(
    importMenuExpanded: Boolean,
    onImportMenuRequest: () -> Unit,
    onMenuDismiss: () -> Unit,
    onImport: () -> Unit,
    onImportFolder: () -> Unit,
    reveal: LaunchReveal,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalLukiaDimens.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = dimens.screenPadding),
    ) {
        // No heading of its own: there is nothing here to head, and "Your library
        // is empty" below is a better one than the word "Library" would be.
        EmptyState(
            title = "Your library is empty",
            // With no explicit button here, the copy has to carry the affordance
            // for the tappable cover above.
            message = "Choose the cover to add a PDF, EPUB, Word or text file.",
            modifier = Modifier
                .align(Alignment.Center)
                .launchReveal(reveal, index = 1),
        ) {
            // The same slot that ends a full library, standing alone here. One
            // card to learn, and it asks the same question in both places.
            ImportBookCard(
                menuExpanded = importMenuExpanded,
                onMenuRequest = onImportMenuRequest,
                onMenuDismiss = onMenuDismiss,
                onImport = onImport,
                onImportFolder = onImportFolder,
                reveal = reveal,
                revealIndex = 1,
                label = null,
                modifier = Modifier.width(EmptyStateCoverWidth),
            )
        }
    }
}
