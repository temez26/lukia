package io.lukia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 12 and up draw a splash screen whether an app asks for one or
        // not, and end it by scaling the launcher icon away. Landing that
        // immediately before the app's own mark arrives reads as the app opening
        // twice, so the splash is claimed here and given nothing to do: the
        // theme leaves its icon blank, and the exit animation is replaced with
        // taking the view away the moment there is a frame behind it. What is
        // left is a field of the app's background colour, which is what the
        // opening starts on anyway.
        installSplashScreen().setOnExitAnimationListener { splash -> splash.remove() }

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }
    }
}

@Preview
@Composable
private fun AppAndroidPreview() {
    App()
}
