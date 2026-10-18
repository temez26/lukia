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
import androidx.compose.ui.graphics.graphicsLayer
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
import io.lukia.shared.presentation.components.controls.LukiaIconButton
import io.lukia.shared.presentation.components.icons.PlusGlyph
import io.lukia.shared.presentation.components.icons.SelectAllGlyph
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/**
 * The bar that takes the shelf's place while books are being picked.
 *
 * Replaces the search bar rather than sitting beside it: a shelf is only being
 * asked one of those questions at a time, and leaving search in place would
 * invite narrowing the shelf while holding a selection about to disagree with
 * what is on it.
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
        // Leaving the mode first, where a back control is: ending a selection is
        // going back to the shelf. Drawn as the app's close mark rather than
        // spelled out, as the photo and file apps set their selection bars.
        LukiaIconButton(
            label = stringResource(Res.string.action_done),
            onClick = onFinish,
            enabled = running == null,
        ) { tint, glyph ->
            PlusGlyph(tint = tint, modifier = glyph.graphicsLayer { rotationZ = 45f })
        }

        Text(
            // While something is running the count is not the news - how far
            // through it is, is.
            text = running?.displayText() ?: selectionTitle(selection),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = Space.sm),
        )

        LukiaIconButton(
            label = stringResource(
                if (allSelected) Res.string.library_deselect_all else Res.string.library_select_all,
            ),
            onClick = onToggleAll,
        ) { tint, glyph ->
            SelectAllGlyph(tint = tint, all = allSelected, modifier = glyph)
        }
    }
}

@Composable
private fun selectionTitle(selection: LibrarySelection): String = if (selection.isEmpty) {
    stringResource(Res.string.library_select_books)
} else {
    stringResource(Res.string.library_selected_count, selection.count)
}
