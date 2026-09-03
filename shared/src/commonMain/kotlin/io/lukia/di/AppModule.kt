package io.lukia.di

import io.lukia.core.domain.diagnostics.Diagnostics
import io.lukia.core.infrastructure.AppCommands
import io.lukia.core.infrastructure.DocumentInbox
import io.lukia.core.infrastructure.IncomingDocuments
import io.lukia.core.infrastructure.db.LukiaDatabase
import io.lukia.core.infrastructure.diagnostics.DiagnosticBuffer
import io.lukia.core.infrastructure.diagnostics.RecordingDiagnostics
import io.lukia.features.library.libraryModule
import io.lukia.features.reader.readerModule
import io.lukia.features.settings.settingsModule
import io.lukia.features.stats.statsModule
import io.lukia.features.translate.translateModule
import org.koin.dsl.module

/**
 * Everything the app is made of that is the same on every platform.
 *
 * Each feature registers its own half of the graph, so what a feature offers the
 * rest of the app is decided beside the code that offers it rather than in one
 * list that has to import every one of them. What is left here is the database,
 * which belongs to no feature and is shared by most of them, and the inbox that
 * the platform fills and the library empties.
 */
val commonModule = module {

    // One database for the whole app, opened once. Each platform's module says
    // where the file lives; everything built on it is the same code.
    single {
        LukiaDatabase(get())
    }

    // One inbox for the process. The platform's own entry point puts documents
    // in - see `MainActivity` and `iOSApp.swift` - and the library takes them
    // out; neither can reach the other, and both can reach this.
    single {
        DocumentInbox()
    }

    single {
        IncomingDocuments(inbox = get(), store = get())
    }

    // What a desktop menu bar asks the app for. Empty on the platforms with no
    // menu bar to ask from, which is every platform but one.
    single {
        AppCommands()
    }

    // Where the boundaries write down what they absorbed. One for the process,
    // because a report is about a run of the app rather than about a screen.
    //
    // In memory and nowhere else: nothing reads this yet and nothing can send it
    // anywhere, which is the point at which it is safe to have it collecting.
    single {
        DiagnosticBuffer()
    }

    single<Diagnostics> {
        RecordingDiagnostics(get())
    }

    includes(
        libraryModule,
        readerModule,
        translateModule,
        settingsModule,
        statsModule,
    )
}
