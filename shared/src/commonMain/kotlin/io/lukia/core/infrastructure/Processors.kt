package io.lukia.core.infrastructure

/**
 * How many processors this device can actually run work on at once.
 *
 * Asked by the one piece of work in the app large enough to be worth dividing -
 * re-setting a PDF as text, which reads every page of a file - so that a phone
 * with four cores and a desktop with sixteen each use what they have rather
 * than what the other has.
 */
internal expect fun availableProcessors(): Int
