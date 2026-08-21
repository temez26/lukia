package io.lukia.features.reader.presentation.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What a finger on the book means: a passage, or the chrome.
 *
 * A passage takes one tap and the chrome takes two. That asymmetry is the whole
 * reason this exists rather than
 * [androidx.compose.foundation.gestures.detectTapGestures]: a single tap that
 * misses - the gap between two sentences, the ragged end of the last line, a
 * translation the reader meant to put back - was read as a tap on the page, and
 * a tap on the page threw the toolbar over the words they were reading. One miss
 * should cost nothing.
 *
 * A tap that does land on a passage is acted on at once, with no wait to see
 * whether a second follows. That is the part `detectTapGestures` cannot do:
 * given a double-tap handler it delays *every* tap by the double-tap window,
 * which in a reading app is a third of a second of nothing happening each time
 * somebody asks about a sentence. Here [onTap] itself says whether the text
 * answered, and only an unanswered tap waits.
 *
 * @param onLongPress where a finger was held down.
 * @param onChrome two taps in a row that both reached nothing.
 * @param onTap where a tap landed; true if the text answered it.
 */
internal suspend fun PointerInputScope.detectReaderTaps(
    onLongPress: (Offset) -> Unit,
    onChrome: () -> Unit,
    onTap: (Offset) -> Boolean,
): Unit = awaitEachGesture {
    val first = awaitTap(awaitFirstDown(), onLongPress) ?: return@awaitEachGesture

    if (onTap(first)) return@awaitEachGesture

    // Nothing under the first one. A second within the double-tap window is the
    // reader asking for the chrome; anything later is a gesture of its own, and
    // this one is simply forgotten.
    val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
        awaitFirstDown()
    } ?: return@awaitEachGesture

    val landed = awaitTap(second, onLongPress) ?: return@awaitEachGesture

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
    onLongPress: (Offset) -> Unit,
): Offset? {
    down.consume()

    val up = try {
        withTimeout(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() }
    } catch (_: PointerEventTimeoutCancellationException) {
        onLongPress(down.position)
        consumeUntilUp()
        return null
    }

    return up?.also { it.consume() }?.position
}

/** Swallows the rest of a gesture already answered, so its release is not a tap. */
private suspend fun AwaitPointerEventScope.consumeUntilUp() {
    do {
        val event = awaitPointerEvent()
        event.changes.forEach { it.consume() }
    } while (event.changes.any { it.pressed })
}
