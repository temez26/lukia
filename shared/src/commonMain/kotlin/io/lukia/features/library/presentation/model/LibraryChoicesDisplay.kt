package io.lukia.features.library.presentation.model

import androidx.compose.runtime.Composable
import io.lukia.resources.Res
import io.lukia.resources.home_shelf_almost_done
import io.lukia.resources.home_shelf_favorites
import io.lukia.resources.home_shelf_finished
import io.lukia.resources.home_shelf_recent
import io.lukia.resources.home_shelf_recently_added
import io.lukia.resources.home_shelf_unread
import io.lukia.resources.shelf_all
import io.lukia.resources.shelf_finished
import io.lukia.resources.shelf_reading
import io.lukia.resources.shelf_unread
import io.lukia.resources.sort_added
import io.lukia.resources.sort_author
import io.lukia.resources.sort_largest
import io.lukia.resources.sort_last_read
import io.lukia.resources.sort_recently_added
import io.lukia.resources.sort_title
import io.lukia.resources.sort_title_reversed
import org.jetbrains.compose.resources.stringResource

/**
 * What the shelves, the orders and the home screen's rows are called.
 *
 * The home rows keep their own names rather than borrowing the shelves': the two
 * sets overlap - both have an Unread - but a row is a sample and a shelf is the
 * whole of it, and a language that distinguishes them needs somewhere to say so.
 */
@Composable
fun LibraryShelf.displayName(): String = when (this) {
    LibraryShelf.All -> stringResource(Res.string.shelf_all)
    LibraryShelf.Reading -> stringResource(Res.string.shelf_reading)
    LibraryShelf.Unread -> stringResource(Res.string.shelf_unread)
    LibraryShelf.Finished -> stringResource(Res.string.shelf_finished)
}

@Composable
fun LibrarySort.displayName(): String = when (this) {
    LibrarySort.Added -> stringResource(Res.string.sort_added)
    LibrarySort.RecentlyAdded -> stringResource(Res.string.sort_recently_added)
    LibrarySort.Title -> stringResource(Res.string.sort_title)
    LibrarySort.TitleReversed -> stringResource(Res.string.sort_title_reversed)
    LibrarySort.Author -> stringResource(Res.string.sort_author)
    LibrarySort.LastRead -> stringResource(Res.string.sort_last_read)
    LibrarySort.Largest -> stringResource(Res.string.sort_largest)
}

@Composable
fun HomeShelf.displayTitle(): String = when (this) {
    HomeShelf.Recent -> stringResource(Res.string.home_shelf_recent)
    HomeShelf.RecentlyAdded -> stringResource(Res.string.home_shelf_recently_added)
    HomeShelf.AlmostDone -> stringResource(Res.string.home_shelf_almost_done)
    HomeShelf.Unread -> stringResource(Res.string.home_shelf_unread)
    HomeShelf.Favorites -> stringResource(Res.string.home_shelf_favorites)
    HomeShelf.Finished -> stringResource(Res.string.home_shelf_finished)
}
