package io.lukia.features.library.presentation.model

import androidx.compose.runtime.Composable
import io.lukia.features.library.domain.LibraryLayout
import io.lukia.resources.Res
import io.lukia.resources.library_layout_grid
import io.lukia.resources.library_layout_list
import org.jetbrains.compose.resources.stringResource

/** What the two ways of laying out a shelf are called. */
@Composable
fun LibraryLayout.displayName(): String = when (this) {
    LibraryLayout.Grid -> stringResource(Res.string.library_layout_grid)
    LibraryLayout.List -> stringResource(Res.string.library_layout_list)
}
