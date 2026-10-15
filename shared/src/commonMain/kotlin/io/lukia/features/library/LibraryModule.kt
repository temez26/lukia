package io.lukia.features.library

import io.lukia.core.domain.document.DocumentCatalogue
import io.lukia.features.library.application.GetDocumentDetailsUseCase
import io.lukia.features.library.application.GetLibraryDocumentsUseCase
import io.lukia.features.library.application.HashLibraryUseCase
import io.lukia.features.library.application.ImportDocumentUseCase
import io.lukia.features.library.application.ImportDocumentsUseCase
import io.lukia.features.library.application.ImportFolderUseCase
import io.lukia.features.library.application.RefreshDocumentUseCase
import io.lukia.features.library.application.RemoveDocumentUseCase
import io.lukia.features.library.application.SetFavoriteUseCase
import io.lukia.features.library.domain.ContentHashStore
import io.lukia.features.library.domain.LibraryRepository
import io.lukia.features.library.domain.ServerShelf
import io.lukia.features.library.infrastructure.LibrarySettingsRepository
import io.lukia.features.library.infrastructure.RecentSearchesRepository
import io.lukia.features.library.infrastructure.SqlContentHashStore
import io.lukia.features.library.infrastructure.SqlLibraryRepository
import io.lukia.features.library.infrastructure.SqlServerShelf
import io.lukia.features.library.presentation.BookDetailViewModel
import io.lukia.features.library.presentation.LibraryViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

val libraryModule = module {

    // One shelf, reachable by two names. The library holds the whole of it; the
    // reader and the statistics screen are handed `DocumentCatalogue`, which is
    // the reading half. Bound rather than registered twice, so there is no way
    // for the two names to end up in front of different databases.
    single<LibraryRepository> {
        SqlLibraryRepository(get())
    } bind DocumentCatalogue::class

    single {
        ImportDocumentUseCase(get(), get())
    }

    single {
        ImportDocumentsUseCase(get(), get())
    }

    single {
        ImportFolderUseCase(get(), get())
    }

    viewModel {
        LibraryViewModel(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            uploader = get(),
            downloader = get(),
        )
    }

    single {
        GetLibraryDocumentsUseCase(get(), get())
    }

    single {
        SetFavoriteUseCase(get())
    }

    single {
        RefreshDocumentUseCase(get(), get(), get())
    }

    single {
        RemoveDocumentUseCase(get(), get(), get(), get(), get())
    }

    single<ContentHashStore> {
        SqlContentHashStore(get())
    }

    single<ServerShelf> {
        SqlServerShelf(get())
    }

    single {
        HashLibraryUseCase(get(), get())
    }

    // How the shelf is laid out is remembered between sessions, and read back by
    // whichever screen asks first.
    single {
        LibrarySettingsRepository(get())
    }

    // A single so that a search made on the search tab is in the history the
    // next time any screen asks for it, without either of them re-reading the
    // store.
    single {
        RecentSearchesRepository(get())
    }

    single {
        GetDocumentDetailsUseCase(get(), get())
    }

    viewModel { (location: String) ->
        BookDetailViewModel(location, get(), get(), get(), get(), get(), get(), get())
    }
}
