package io.lukia.di

import io.lukia.features.import.domain.DesktopDocumentAnalyzer
import io.lukia.features.import.domain.DocumentAnalyzer
import io.lukia.features.import.presentation.DesktopImportFilePicker
import io.lukia.features.import.presentation.ImportFilePicker
import org.koin.core.module.Module
import org.koin.dsl.module

val platformModule = module {

    single<ImportFilePicker> {
        DesktopImportFilePicker()
    }

    single<DocumentAnalyzer> {
        DesktopDocumentAnalyzer()
    }
}