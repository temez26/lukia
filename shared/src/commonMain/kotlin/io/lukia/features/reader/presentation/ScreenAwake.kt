package io.lukia.features.reader.presentation

import androidx.compose.runtime.Composable

/**
 * Asks the device not to dim and lock while [enabled], for as long as this is in
 * the composition.
 *
 * A page being read is a page nobody is touching, and every device treats "not
 * touched" as "not being used" - so without this a phone dims mid-paragraph and
 * locks, the oldest complaint there is about reading on one.
 *
 * Held by the screen rather than set once at launch, so the request lasts
 * exactly as long as a book is open and closing the reader gives the device its
 * timeout back without anything having to remember to.
 *
 * Nothing on the desktop, where the reader is one window among many and a
 * screensaver is the operating system's business rather than a book's.
 */
@Composable
expect fun KeepScreenAwake(enabled: Boolean)
