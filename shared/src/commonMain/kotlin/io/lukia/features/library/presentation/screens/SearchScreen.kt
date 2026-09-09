package io.lukia.features.library.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.features.library.presentation.LibraryViewModel
import io.lukia.features.library.presentation.components.book.BookRow
import io.lukia.features.library.presentation.components.search.LibrarySearchField
import io.lukia.features.library.presentation.components.search.RecentSearches
import io.lukia.features.library.presentation.model.LibraryBook
import io.lukia.resources.Res
import io.lukia.resources.library_no_matches_term
import io.lukia.resources.library_no_results_title
import io.lukia.resources.search_empty_message
import io.lukia.resources.search_empty_title
import io.lukia.resources.search_placeholder
import io.lukia.shared.presentation.components.surface.EmptyState
import io.lukia.shared.presentation.components.surface.SectionLabel
import io.lukia.shared.presentation.theme.Clearance
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

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
    val search = viewModel.search
    val term by search.term.collectAsState()
    val results by search.results.collectAsState()
    val recent by search.recent.collectAsState()

    SearchContent(
        term = term,
        onTermChange = search::update,
        results = results,
        onOpenBook = { book ->
            // Opening a result is the clearest sign a search was finished, and
            // it catches the reader who never presses the search key because the
            // list narrowed as they typed.
            search.submit()
            onOpenBook(book.document)
        },
        onShowDetails = { book -> onShowDetails(book.document) },
        onSubmit = search::submit,
        recent = recent,
        onForgetRecent = search::forget,
        onClearRecent = search::clearRecent,
    )
}

/**
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
    onSubmit: () -> Unit = {},
    /** What has been searched for before, newest first. */
    recent: List<String> = emptyList(),
    onForgetRecent: (String) -> Unit = {},
    onClearRecent: () -> Unit = {},
) {
    val dimens = LocalLukiaDimens.current

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        // See `LibraryContent`: the cutout is not in the Scaffold's default.
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            LibrarySearchField(
                query = term,
                onQueryChange = onTermChange,
                placeholder = stringResource(Res.string.search_placeholder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.screenPadding, vertical = Space.md),
                onSubmit = onSubmit,
            )

            SearchBody(
                term = term,
                results = results,
                onOpenBook = onOpenBook,
                onShowDetails = onShowDetails,
                recent = recent,
                onSearchAgain = onTermChange,
                onForgetRecent = onForgetRecent,
                onClearRecent = onClearRecent,
            )
        }
    }
}

/**
 * What sits under the field: the results, why there are none, or - before
 * anything has been typed - the searches already made.
 *
 * The history earns the empty screen over a sentence explaining what searching
 * is. A reader looking at an empty search field has either just arrived or just
 * cleared it, and in both cases the words they have already found books with are
 * worth more than the explanation. The sentence is kept for the reader who has
 * no history yet, who is the only one it tells anything.
 */
@Composable
private fun SearchBody(
    term: String,
    results: List<LibraryBook>,
    onOpenBook: (LibraryBook) -> Unit,
    onShowDetails: (LibraryBook) -> Unit,
    recent: List<String>,
    onSearchAgain: (String) -> Unit,
    onForgetRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
) {
    when {
        term.isNotBlank() && results.isEmpty() -> SearchNotice(
            title = stringResource(Res.string.library_no_results_title),
            message = stringResource(Res.string.library_no_matches_term, term.trim()),
        )

        term.isNotBlank() -> SearchResults(
            results = results,
            onOpenBook = onOpenBook,
            onShowDetails = onShowDetails,
            modifier = Modifier.fillMaxSize(),
        )

        recent.isNotEmpty() -> SearchHistory(
            recent = recent,
            onSearch = onSearchAgain,
            onForget = onForgetRecent,
            onClear = onClearRecent,
            modifier = Modifier.fillMaxSize(),
        )

        else -> SearchNotice(
            title = stringResource(Res.string.search_empty_title),
            message = stringResource(Res.string.search_empty_message),
        )
    }
}

@Composable
private fun SearchHistory(
    recent: List<String>,
    onSearch: (String) -> Unit,
    onForget: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalLukiaDimens.current

    // Scrollable rather than lazy: the history is capped at a handful of rows -
    // see RecentSearchesRepository - and a lazy list for eight short lines is
    // machinery for nothing. It scrolls at all only so the keyboard cannot trap
    // the last row underneath itself on a short screen.
    Box(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(
                start = dimens.screenPadding,
                end = dimens.screenPadding,
                bottom = Clearance.listBottom,
            ),
    ) {
        RecentSearches(
            terms = recent,
            onSearch = onSearch,
            onForget = onForget,
            onClear = onClear,
        )
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
            bottom = Clearance.listBottom,
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
                book = book,
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
