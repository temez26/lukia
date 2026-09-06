package io.lukia

import androidx.compose.ui.window.ComposeUIViewController
import io.lukia.di.commonModule
import io.lukia.di.platformModule
import org.koin.core.context.startKoin

// Named for the Objective-C entry point Xcode calls, not for Kotlin's convention.
// Both linters have a rule for this, and both have to be told.
@Suppress("ktlint:standard:function-naming", "FunctionNaming")
fun MainViewController() = run {
    startKoin {
        modules(
            commonModule,
            platformModule,
        )
    }

    ComposeUIViewController {
        App()
    }
}
