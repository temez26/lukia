package io.lukia.features.library.presentation.model

import androidx.compose.runtime.Composable
import io.lukia.core.presentation.authorLine
import io.lukia.core.presentation.bookTitle
import io.lukia.shared.presentation.components.menu.BookMenuHeader

/** This book as the top of the phone's book menu shows it - see [BookMenuHeader]. */
@Composable
internal fun LibraryBook.menuHeader(): BookMenuHeader = BookMenuHeader(
    title = document.bookTitle(),
    author = document.metadata?.authorLine(),
    cover = cover,
    percent = progress?.percent,
)
