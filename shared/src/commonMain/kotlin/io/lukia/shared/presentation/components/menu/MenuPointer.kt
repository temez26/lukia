package io.lukia.shared.presentation.components.menu

import androidx.compose.ui.geometry.Offset
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Where, in the window, the pointer last asked for a menu with its second button.
 *
 * A context menu opens where it was asked for, as every desktop's does; one
 * hanging off the corner of the card that was right-clicked reads as belonging
 * to the card's edge rather than to the click. Recorded by `onSecondaryPress` and
 * taken by the [AnchoredMenu] that the press opens, so neither has to be told
 * about the other through every card that uses both.
 *
 * Read only while fresh, so a right click that opened something other than an
 * [AnchoredMenu] does not place the next menu opened from a button. Read during
 * composition and cleared once that composition has been applied - see
 * [AnchoredMenu] - so a composition that is thrown away does not lose it.
 */
internal object MenuPointer {

    private val Fresh = 500.milliseconds

    private var position: Offset? = null
    private var pressedAt: TimeMark? = null

    fun record(windowPosition: Offset) {
        position = windowPosition
        pressedAt = TimeSource.Monotonic.markNow()
    }

    fun peek(): Offset? {
        val fresh = pressedAt?.let { it.elapsedNow() < Fresh } == true
        return position.takeIf { fresh }
    }

    fun clear() {
        position = null
        pressedAt = null
    }
}
