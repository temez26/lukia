package io.lukia.di

import io.lukia.features.import.domain.DocumentAnalyzer
import io.lukia.features.import.domain.IOSDocumentAnalyzer
import org.koin.dsl.module

val platformModule = module {

    single<DocumentAnalyzer> {
        IOSDocumentAnalyzer()
    }

}