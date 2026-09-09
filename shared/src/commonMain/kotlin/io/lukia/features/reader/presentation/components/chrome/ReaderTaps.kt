package io.lukia.features.reader.presentation.components.chrome

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What a finger on the book means: a passage, or the chrome.
 *
 * A passage takes one tap and the chrome takes two. That asymmetry is why this
 * exists rather than
 * [androidx.compose.foundation.gestures.detectTapGestures]: a single tap that
 * misses - the gap between two sentences, the ragged end of a line - was read as
 * a tap on the page, which threw the toolbar over the words being read. One miss
 * should cost nothing.
 *
 * A tap that lands on a passage is acted on at once, with no wait for a second.
 * That is what `detectTapGestures` cannot do: given a double-tap handler it
 * delays *every* tap by the double-tap window, a third of a second of nothing
 * happening each time somebody asks about a sentence. Here [onTap] says whether
 * the text answered, and only an unanswered tap waits.
 *
 * The second mouse button - two fingers on a trackpad - is its own question,
 * answered at once rather than after the half second a hold takes. It goes to
 * [onQuestion] rather than [onHold] because a hold is the reader's own gesture
 * with their own setting behind it, while this is what every desktop already
 * means "tell me about this" with.
 *
 * @param onHold where a finger was held down.
 * @param onQuestion where the second mouse button was pressed.
 * @param onChrome two taps in a row that both reached nothing.
 * @param onTap where a tap landed; true if the text answered it.
 */
internal suspend fun PointerInputScope.detectReaderTaps(
    onHold: (Offset) -> Unit,
    onQuestion: (Offset) -> Unit,
    onChrome: () -> Unit,
    onTap: (Offset) -> Boolean,
): Unit = awaitEachGesture {
    val press = awaitFirstPress()

    if (currentEvent.buttons.isSecondaryPressed) {
        press.consume()
        onQuestion(press.position)
        consumeUntilUp()

        return@awaitEachGesture
    }

    val first = awaitTap(press, onHold) ?: return@awaitEachGesture

    if (onTap(first)) return@awaitEachGesture

    // Nothing under the first one. A second within the double-tap window is the
    // reader asking for the chrome; anything later is a gesture of its own, and
    // this one is simply forgotten.
    val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
        awaitFirstDown()
    } ?: return@awaitEachGesture

    val landed = awaitTap(second, onHold) ?: return@awaitEachGesture

    // The second tap is still a tap on a passage in its own right: a reader who
    // missed and corrected themselves asked about the sentence they hit the
    // second time, not for the toolbar.
    if (!onTap(landed)) onChrome()
}

/**
 * Waits out one press, and gives back where it was released - or null when it
 * turned into something else: a long press, which is reported here and
 * swallowed, or a drag that the scrolling underneath took over.
 */
private suspend fun AwaitPointerEventScope.awaitTap(
    down: PointerInputChange,
    onHold: (Offset) -> Unit,
): Offset? {
    down.consume()

    val up = try {
        withTimeout(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() }
    } catch (_: PointerEventTimeoutCancellationException) {
        onHold(down.position)
        consumeUntilUp()
        return null
    }

    return up?.also { it.consume() }?.position
}

/**
 * The press that begins a gesture, whichever button made it.
 *
 * Written out rather than left to [awaitFirstDown], which on desktop answers to
 * the primary mouse button only - so the second button never began a gesture and
 * the branch above could not be reached. The behaviour differs between desktop
 * and Android for reasons Compose itself calls a thing to align one day, which
 * is reason enough not to build a gesture on it.
 *
 * The two buttons a reader has are taken and the rest are left. A middle click
 * is a scroll on most machines and a paste on some, and neither is a question
 * about the sentence under it.
 */
private suspend fun AwaitPointerEventScope.awaitFirstPress(): PointerInputChange {
    while (true) {
        val event = awaitPointerEvent()
        val press = event.changes.firstOrNull { it.changedToDown() }

        if (press != null && event.beginsAGesture(press)) return press
    }
}

/** Whether this press is one of the two a reader points at a book with. */
private fun PointerEvent.beginsAGesture(press: PointerInputChange): Boolean =
    press.type != PointerType.Mouse || buttons.isPrimaryPressed || buttons.isSecondaryPressed

/** Swallows the rest of a gesture already answered, so its release is not a tap. */
private suspend fun AwaitPointerEventScope.consumeUntilUp() {
    do {
        val event = awaitPointerEvent()
        event.changes.forEach { it.consume() }
    } while (event.changes.any { it.pressed })
}
