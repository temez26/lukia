package io.lukia.di

import io.lukia.core.domain.DocumentWorkspace
import io.lukia.core.infrastructure.JvmDocumentWorkspace
import io.lukia.features.import.domain.DocumentAnalyzer
import io.lukia.features.import.domain.FolderScanner
import io.lukia.features.import.infrastructure.DesktopDocumentAnalyzer
import io.lukia.features.import.infrastructure.JvmFolderScanner
import org.koin.dsl.module

val platformModule = module {

    single<DocumentAnalyzer> {
        DesktopDocumentAnalyzer()
    }

    single<DocumentWorkspace> {
        JvmDocumentWorkspace()
    }

    single<FolderScanner> {
        JvmFolderScanner()
    }
}
