package io.lukia.shared.presentation.theme

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Android has no "reduce motion" switch of its own, only the two animation
 * scales its accessibility guidance and its own components read: somebody who
 * turned animations off has set both to zero.
 *
 * Both are read because they cover different animations -
 * `TRANSITION_ANIMATION_SCALE` is a window arriving, `ANIMATOR_DURATION_SCALE` a
 * property animating inside one - and this app's motion mixes the two. Off in
 * either is taken as off.
 *
 * Observed rather than read once: the setting can change while the app runs, and
 * requiring a restart would ask somebody to sit through the very thing they just
 * turned off.
 */
@Composable
actual fun systemReducesMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver

    var reduced by remember(resolver) { mutableStateOf(resolver.animationsAreOff()) }

    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                reduced = resolver.animationsAreOff()
            }
        }

        AnimationScaleKeys.forEach { key ->
            resolver.registerContentObserver(Settings.Global.getUriFor(key), false, observer)
        }

        onDispose { resolver.unregisterContentObserver(observer) }
    }

    return reduced
}

private val AnimationScaleKeys = listOf(
    Settings.Global.TRANSITION_ANIMATION_SCALE,
    Settings.Global.ANIMATOR_DURATION_SCALE,
)

/**
 * A scale of zero is the platform's way of spelling "do not animate". The
 * default of 1 is what an unset value means, which is also what a device with
 * the settings untouched reports.
 */
private fun ContentResolver.animationsAreOff(): Boolean =
    AnimationScaleKeys.any { Settings.Global.getFloat(this, it, 1f) == 0f }
