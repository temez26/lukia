package io.lukia

import androidx.compose.ui.window.ComposeUIViewController
import io.lukia.di.commonModule
import io.lukia.di.platformModule
import org.koin.core.context.startKoin

fun MainViewController() = ComposeUIViewController {
    
    startKoin {
        modules(
            commonModule,
            platformModule
        )
    }

    App() 

}