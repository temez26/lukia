package io.lukia

import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController {
    
    startKoin {
        modules(
            commonModule,
            platformModule
        )
    }

    App() 

}