package io.lukia.di

import io.lukia.core.infrastructure.db.LukiaDatabase
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
 * which belongs to no feature and is shared by most of them.
 */
val commonModule = module {

    // One database for the whole app, opened once. Each platform's module says
    // where the file lives; everything built on it is the same code.
    single {
        LukiaDatabase(get())
    }

    includes(
        libraryModule,
        readerModule,
        translateModule,
        settingsModule,
        statsModule,
    )
}
