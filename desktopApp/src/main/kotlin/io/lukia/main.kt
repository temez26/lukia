package io.lukia

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.lukia.di.commonModule
import io.lukia.di.platformModule
import org.koin.core.context.startKoin

fun main() {

    startKoin {
        modules(
            commonModule,
            platformModule
        )
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Lukia",
        ) {
            App()
        }
    }
}