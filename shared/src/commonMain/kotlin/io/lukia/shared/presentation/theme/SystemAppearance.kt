package io.lukia.shared.presentation.theme

import androidx.compose.runtime.Composable
import io.lukia.features.settings.domain.AppTheme

/**
 * Tells the system which appearance the app is wearing.
 *
 * Everything Lukia draws it draws itself, so for the length of a session this
 * changes nothing at all. It exists for the frames before that: the window an
 * app opens into is painted by the system, from a colour chosen before a line of
 * app code has run, and the system's only way of choosing it is to ask whether
 * the *device* is in dark mode. A reader who keeps a light phone and a dark
 * Lukia therefore gets a pale flash in front of a black app every time it opens
 * - and no in-app animation can smooth that over, because the app is not
 * drawing yet.
 *
 * Where a platform will take the answer, this gives it, so that the colour the
 * system picks is the colour the app is about to paint.
 */
@Composable
expect fun SystemAppearanceEffect(theme: AppTheme)
