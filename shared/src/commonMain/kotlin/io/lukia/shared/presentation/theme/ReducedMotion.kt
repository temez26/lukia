package io.lukia.shared.presentation.theme

import androidx.compose.runtime.Composable

/**
 * Whether the device has been asked to move less.
 *
 * Both platforms have this setting, both expect an app to read it, and neither
 * will apply it on the app's behalf: Compose draws its own animations through
 * Skia rather than through the system's animators, so nothing the reader turned
 * off in Settings reaches a single frame of this app on its own.
 *
 * It matters here more than in most apps: reduced motion is asked for by people
 * for whom motion is not a preference - vestibular disorders, migraine, motion
 * sickness - and this app rocks every cover on a shelf while books are picked,
 * runs a light along a sentence while a translation arrives, and fans a mark
 * open on every launch.
 *
 * Read as a composable rather than a value because it is live on both platforms:
 * somebody who turns it on mid-session should not restart to be believed.
 *
 * See [motionFor], which caps rather than replaces the reader's own choice: the
 * system says how much motion is allowed, and [io.lukia.core.domain.AppMotion]
 * says how much of what is left the reader wants.
 */
@Composable
expect fun systemReducesMotion(): Boolean
