package io.lukia

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.lukia.di.commonModule
import io.lukia.di.platformModule
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.koin.core.context.startKoin

private object Resources

private val windowIcon: Painter by lazy {
    val icon = checkNotNull(Resources::class.java.getResourceAsStream("/icons/lukia.png")) {
        "Missing window icon: /icons/lukia.png"
    }
    BitmapPainter(icon.use { it.readAllBytes() }.decodeToImageBitmap())
}

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
            icon = windowIcon,
        ) {
            App()
        }
    }
}
