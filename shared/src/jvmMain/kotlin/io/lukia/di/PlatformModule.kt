package io.lukia.di

import io.lukia.features.import.domain.DesktopDocumentAnalyzer
import io.lukia.features.import.domain.DocumentAnalyzer
import io.lukia.features.import.infrastructure.DesktopImportFilePicker
import io.lukia.features.import.infrastructure.ImportFilePicker
import org.koin.dsl.module

val platformModule = module {

    single<ImportFilePicker> {
        DesktopImportFilePicker()
    }

    single<DocumentAnalyzer> {
        DesktopDocumentAnalyzer()
    }
}