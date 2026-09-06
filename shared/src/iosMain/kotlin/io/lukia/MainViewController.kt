package io.lukia

import androidx.compose.ui.window.ComposeUIViewController
import io.lukia.di.commonModule
import io.lukia.di.platformModule
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatformTools

// Named for the Objective-C entry point Xcode calls, not for Kotlin's convention.
// Both linters have a rule for this, and both have to be told.
@Suppress("ktlint:standard:function-naming", "FunctionNaming")
fun MainViewController() = run {
    startAppOnce()

    ComposeUIViewController {
        App()
    }
}

/**
 * Wires the app up the first time this is called, and does nothing after that.
 *
 * The guard is not defensive tidying. This is the factory behind a SwiftUI
 * `UIViewControllerRepresentable`, and SwiftUI decides for itself how often to
 * make one: a scene rebuilt after the app is backgrounded, or a `ContentView`
 * re-identified for any of the reasons SwiftUI re-identifies a view, calls this
 * again in the same process. Koin answers a second `startKoin` by throwing, so
 * what the reader saw was the app dying on being returned to.
 *
 * Asked of Koin rather than kept in a flag of our own, so that a graph started
 * by anything else - a test, a future second entry point - is also left alone.
 */
private fun startAppOnce() {
    if (KoinPlatformTools.defaultContext().getOrNull() != null) return

    startKoin {
        modules(
            commonModule,
            platformModule,
        )
    }
}
