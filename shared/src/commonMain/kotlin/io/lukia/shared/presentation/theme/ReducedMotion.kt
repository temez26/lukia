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
 * It matters here more than it would in most apps. Reduced motion is asked for
 * by people for whom motion is not a preference - vestibular disorders, migraine,
 * motion sickness - and this app rocks every cover on a shelf while books are
 * being picked, runs a light along a sentence while it waits for a translation,
 * and fans a mark open on every launch.
 *
 * Read as a composable rather than as a value because it is a live setting on
 * both platforms: somebody who turns it on mid-session should not have to
 * restart a reading app to be believed.
 *
 * See [motionFor], which is what the answer is actually used for - and note that
 * it caps rather than replaces the reader's own choice. The system says how much
 * motion is allowed; [io.lukia.core.domain.AppMotion] says how much of what is
 * left the reader wants.
 */
@Composable
expect fun systemReducesMotion(): Boolean
