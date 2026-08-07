package io.lukia.di

import io.lukia.features.import.domain.AndroidDocumentAnalyzer
import io.lukia.features.import.domain.DocumentAnalyzer
import org.koin.dsl.module

val platformModule = module {

    single<DocumentAnalyzer> {
        AndroidDocumentAnalyzer(get())
    }

}