package io.lukia.features.library.presentation.components.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.lukia.features.library.presentation.components.toolbar.ImportMenuRows
import io.lukia.resources.Res
import io.lukia.resources.home_add_books
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/**
 * Where books come from, as a sheet from the bottom: chosen one by one, or a
 * whole folder of them. One press on Home's "Add books" opens it, rather than
 * the two ways in standing side by side on the page.
 *
 * Dimmed behind by Home's own scrim, as the book sheet is - see `BookActionsSheet`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddBooksSheet(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onImport: () -> Unit,
    onImportFolder: () -> Unit,
) {
    if (!expanded) return

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        scrimColor = Color.Transparent,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(horizontal = Space.lg).padding(bottom = Space.lg)) {
            Text(
                text = stringResource(Res.string.home_add_books),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = Space.sm),
            )

            ImportMenuRows(
                onDismissRequest = onDismissRequest,
                onImport = onImport,
                onImportFolder = onImportFolder,
            )
        }
    }
}
