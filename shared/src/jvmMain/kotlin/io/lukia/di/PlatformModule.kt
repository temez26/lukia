package io.lukia.di

import io.lukia.features.import.domain.DocumentAnalyzer
import io.lukia.features.import.infrastructure.DesktopDocumentAnalyzer
import org.koin.dsl.module

val platformModule = module {

    single<DocumentAnalyzer> {
        DesktopDocumentAnalyzer()
    }
}