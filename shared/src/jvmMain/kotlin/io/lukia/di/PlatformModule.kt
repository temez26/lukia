package io.lukia.di

import io.lukia.features.import.domain.DesktopDocumentAnalyzer
import io.lukia.features.import.domain.DocumentAnalyzer
import org.koin.dsl.module

val platformModule = module {

    single<DocumentAnalyzer> {
        DesktopDocumentAnalyzer()
    }
}