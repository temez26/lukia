package io.lukia

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.lukia.core.infrastructure.deliverIncomingDocuments

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 12 and up draw a splash whether an app asks or not, ending it
        // by scaling the launcher icon away - which immediately before the app's
        // own mark reads as opening twice. So the splash is claimed here and
        // given nothing to do: the theme leaves its icon blank and the exit
        // animation becomes removing the view once there is a frame behind it,
        // leaving the background colour the opening starts on anyway.
        installSplashScreen().setOnExitAnimationListener { splash -> splash.remove() }

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }

        // A cold start from a tapped book. The inbox holds it until the
        // library has been composed and starts collecting.
        deliverIncomingDocuments(intent)
    }

    /**
     * A book tapped while Lukia is already open.
     *
     * The activity is `singleTask`, so the running one is handed the intent
     * rather than a second copy of the app being stacked on the first - see the
     * manifest. Without this the book would arrive at an activity nobody is
     * looking at.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deliverIncomingDocuments(intent)
    }

    /**
     * The activity survives a change of appearance rather than being rebuilt for
     * one - see the manifest - so the only thing that has to be told about it is
     * the system bars: their icons are drawn light or dark from whichever mode
     * was in force when the edge-to-edge styles were applied, and that is no
     * longer the launch.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        enableEdgeToEdge()
    }
}

@Preview
@Composable
private fun AppAndroidPreview() {
    App()
}
