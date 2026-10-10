package io.lukia.shared.presentation.theme

import androidx.compose.runtime.Composable

/**
 * Draws the system bars' icons dark or light for as long as this is composed,
 * and puts them back as they were afterwards.
 *
 * The system picks them from the device's appearance, and the app does not
 * always wear it: the reader has a theme of its own, so a light page on a dark
 * device had a white clock over it.
 */
@Composable
expect fun SystemBarIcons(dark: Boolean)
