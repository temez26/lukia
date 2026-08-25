package io.lukia.features.library.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.domain.DocumentInfo
import io.lukia.features.library.presentation.LibraryBook
import io.lukia.features.library.presentation.LibraryViewModel
import io.lukia.features.library.presentation.components.BookRow
import io.lukia.features.library.presentation.components.LibrarySearchField
import io.lukia.shared.presentation.components.EmptyState
import io.lukia.shared.presentation.components.SectionLabel
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import org.koin.compose.viewmodel.koinViewModel

/** Clears the last result of the tab bar the list scrolls up to. */
private val ResultsBottomPadding: Dp = 24.dp

/** How far down an empty screen its notice sits, so it is not against the field. */
private val NoticeTopPadding: Dp = 64.dp

/**
 * Searching the library, as its own screen.
 *
 * A tab rather than a field on the shelf. Searching and browsing are different
 * things done at different moments - browsing is "show me what I have",
 * searching is "I know what I want" - and a phone has room to do one of them
 * well at a time. Everything the library tab is narrowed to is ignored here on
 * purpose: somebody looking for a book is looking among all of them.
 *
 * Results are rows rather than covers. A search is answered by reading, and a
 * row carries the author, the format and how far through it its reader is, none
 * of which a thumbnail has room for.
 */
@Composable
internal fun SearchScreen(
    onOpenBook: (DocumentInfo) -> Unit,
    onShowDetails: (DocumentInfo) -> Unit,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val term by viewModel.search.term.collectAsState()
    val results by viewModel.search.results.collectAsState()

    SearchContent(
        term = term,
        onTermChange = viewModel.search::update,
        results = results,
        onOpenBook = { book -> onOpenBook(book.document) },
        onShowDetails = { book -> onShowDetails(book.document) },
    )
}

/**
 * Stateless half of the screen, so the layout can be composed in a test or a
 * preview without a ViewModel or a dependency graph behind it. Every screen in
 * the app splits this way.
 *
 * [results] arrives already found and ordered - the matching is the ViewModel's
 * work, done off the thread that draws - so this screen only ever lists what it
 * is handed.
 */
@Composable
internal fun SearchContent(
    term: String,
    onTermChange: (String) -> Unit,
    results: List<LibraryBook>,
    onOpenBook: (LibraryBook) -> Unit,
    onShowDetails: (LibraryBook) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalLukiaDimens.current

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            LibrarySearchField(
                query = term,
                onQueryChange = onTermChange,
                placeholder = "Search by title or author",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.screenPadding, vertical = Space.md),
            )

            when {
                term.isBlank() -> SearchNotice(
                    title = "Search Your Library",
                    message = "Type a title or an author to find a book.",
                )

                results.isEmpty() -> SearchNotice(
                    title = "No Results",
                    message = "No books match “${term.trim()}”.",
                )

                else -> SearchResults(
                    results = results,
                    onOpenBook = onOpenBook,
                    onShowDetails = onShowDetails,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun SearchResults(
    results: List<LibraryBook>,
    onOpenBook: (LibraryBook) -> Unit,
    onShowDetails: (LibraryBook) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalLukiaDimens.current

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = dimens.screenPadding,
            end = dimens.screenPadding,
            bottom = ResultsBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        item(key = "count", contentType = "count") {
            SectionLabel(
                text = resultCount(results.size),
                modifier = Modifier.padding(bottom = Space.sm),
            )
        }

        items(
            items = results,
            key = { book -> book.location },
            contentType = { "result" },
        ) { book ->
            BookRow(
                document = book.document,
                progress = book.progress,
                onClick = { onOpenBook(book) },
                // A search result is a book somebody is deciding about, so the
                // press that is not "open it" is "tell me more" rather than the
                // shelf-keeping menu the library tab offers.
                onMenuRequest = { onShowDetails(book) },
                longClickLabel = "Book details",
            )
        }
    }
}

private fun resultCount(size: Int): String = if (size == 1) "1 result" else "$size results"

@Composable
private fun SearchNotice(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier
            .fillMaxSize()
            .padding(top = NoticeTopPadding),
    ) {
        EmptyState(title = title, message = message)
    }
}
