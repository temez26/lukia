package io.lukia.features.library.presentation.components.selection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import io.lukia.features.library.presentation.components.toolbar.libraryNavBarHeight
import io.lukia.features.library.presentation.model.BulkAction
import io.lukia.features.library.presentation.model.LibrarySelection
import io.lukia.features.library.presentation.model.displayText
import io.lukia.resources.Res
import io.lukia.resources.action_done
import io.lukia.resources.library_deselect_all
import io.lukia.resources.library_select_all
import io.lukia.resources.library_select_books
import io.lukia.resources.library_selected_count
import io.lukia.shared.presentation.components.controls.ButtonSize
import io.lukia.shared.presentation.components.controls.ButtonVariant
import io.lukia.shared.presentation.components.controls.LukiaButton
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/**
 * The bar that takes the shelf's place while books are being picked.
 *
 * It replaces the search bar rather than sitting beside it, because the two are
 * answers to different questions and a shelf can only be being asked one of them
 * at a time - and because leaving search in place would invite a reader to
 * narrow the shelf while holding a selection that is about to disagree with what
 * they can see.
 *
 * What it says is the count, and never a list: forty books cannot be named in a
 * bar, and a reader who wants to know which forty can see the ticks on them.
 */
@Composable
internal fun LibrarySelectionBar(
    selection: LibrarySelection,
    running: BulkAction?,
    allSelected: Boolean,
    onToggleAll: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalLukiaDimens.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .heightIn(min = libraryNavBarHeight())
            .padding(horizontal = dimens.screenPadding),
    ) {
        LukiaButton(
            label = stringResource(
                if (allSelected) Res.string.library_deselect_all else Res.string.library_select_all,
            ),
            onClick = onToggleAll,
            variant = ButtonVariant.Ghost,
            size = ButtonSize.Compact,
        )

        Text(
            // While something is running the count is not the news - how far
            // through it is, is.
            text = running?.displayText() ?: selectionTitle(selection),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = Space.sm),
        )

        LukiaButton(
            label = stringResource(Res.string.action_done),
            onClick = onFinish,
            variant = ButtonVariant.Ghost,
            size = ButtonSize.Compact,
            enabled = running == null,
        )
    }
}

@Composable
private fun selectionTitle(selection: LibrarySelection): String = if (selection.isEmpty) {
    stringResource(Res.string.library_select_books)
} else {
    stringResource(Res.string.library_selected_count, selection.count)
}
