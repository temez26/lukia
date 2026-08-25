package io.lukia.features.library.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import io.lukia.shared.presentation.components.LukiaChip
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space

/**
 * The subjects the library's own books declare, as a way into each of them.
 *
 * Chips rather than covers, because a category is a word and not a thing to
 * look at - and because the row is a list of doors rather than a sample of what
 * is behind them. Nothing is selected here: pressing one leaves for the library
 * tab, so a chip marked as set would be describing a screen the reader is on
 * their way off.
 *
 * The categories arrive already reduced to the ones that divide the library -
 * see `LibraryFacets` - so a row of them is never every book under one heading.
 */
@Composable
internal fun HomeCategoryRow(
    categories: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalLukiaDimens.current

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Browse by Category",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = dimens.screenPadding),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = dimens.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(Space.md),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.lg),
        ) {
            items(
                items = categories,
                key = { category -> category },
                contentType = { "category" },
            ) { category ->
                LukiaChip(
                    label = category,
                    selected = false,
                    onClick = { onSelect(category) },
                )
            }
        }
    }
}
