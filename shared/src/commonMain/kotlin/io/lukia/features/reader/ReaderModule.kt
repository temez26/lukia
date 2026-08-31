package io.lukia.features.reader

import io.lukia.core.domain.reading.PdfLayoutRepository
import io.lukia.core.domain.reading.ReadingProgressRepository
import io.lukia.core.domain.reading.ReadingSessionRepository
import io.lukia.features.reader.application.OpenDocumentUseCase
import io.lukia.features.reader.infrastructure.ReaderSettingsRepository
import io.lukia.features.reader.infrastructure.SqlPdfLayoutRepository
import io.lukia.features.reader.infrastructure.SqlReadingProgressRepository
import io.lukia.features.reader.infrastructure.SqlReadingSessionRepository
import io.lukia.features.reader.presentation.BookReadingFactory
import io.lukia.features.reader.presentation.ReaderViewModel
import io.lukia.features.reader.presentation.translation.ReaderTranslationsFactory
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val readerModule = module {

    single<ReadingProgressRepository> {
        SqlReadingProgressRepository(get())
    }

    // How long each book has been read for, which is a different question from
    // where the reader is in it - see `ReadingSession.sq`.
    single<ReadingSessionRepository> {
        SqlReadingSessionRepository(get())
    }

    // Which way each PDF is read, remembered per book - a choice about one
    // document rather than a setting on the reader, so it is stored beside the
    // reading position rather than with the preferences.
    single<PdfLayoutRepository> {
        SqlPdfLayoutRepository(get())
    }

    single {
        OpenDocumentUseCase(get(), get())
    }

    // A single instance: reader settings are one shared, live value, so a change
    // made while reading is already in place the next time a book is opened.
    single {
        ReaderSettingsRepository(get())
    }

    // Where each book was left off and how long it has been read for, minus the
    // one thing only the ViewModel has - see BookReadingFactory.
    single {
        BookReadingFactory(get(), get())
    }

    // What the reader needs to translate with, minus the one thing only the
    // ViewModel has - see ReaderTranslationsFactory.
    single {
        ReaderTranslationsFactory(get(), get())
    }

    viewModel { (location: String) ->
        ReaderViewModel(location, get(), get(), get(), get(), get())
    }
}
