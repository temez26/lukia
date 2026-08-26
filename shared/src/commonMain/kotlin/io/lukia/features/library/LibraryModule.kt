package io.lukia.features.library

import io.lukia.features.library.application.GetLibraryDocumentsUseCase
import io.lukia.features.library.application.ImportDocumentUseCase
import io.lukia.features.library.application.ImportDocumentsUseCase
import io.lukia.features.library.application.ImportFolderUseCase
import io.lukia.features.library.application.RefreshDocumentUseCase
import io.lukia.features.library.application.RemoveDocumentUseCase
import io.lukia.features.library.application.SetFavoriteUseCase
import io.lukia.features.library.domain.LibraryRepository
import io.lukia.features.library.infrastructure.LibrarySettingsRepository
import io.lukia.features.library.infrastructure.RecentSearchesRepository
import io.lukia.features.library.infrastructure.SqlLibraryRepository
import io.lukia.features.library.presentation.LibraryViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val libraryModule = module {

    single<LibraryRepository> {
        SqlLibraryRepository(get())
    }

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
        LibraryViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get())
    }

    single {
        GetLibraryDocumentsUseCase(get())
    }

    single {
        SetFavoriteUseCase(get())
    }

    single {
        RefreshDocumentUseCase(get(), get())
    }

    single {
        RemoveDocumentUseCase(get(), get(), get(), get())
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
}
