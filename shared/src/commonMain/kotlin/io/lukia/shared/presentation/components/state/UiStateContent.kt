package io.lukia.shared.presentation.components.state

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.displayMessage
import io.lukia.core.presentation.displayTitle
import io.lukia.shared.presentation.theme.LocalMotionStyle
import io.lukia.shared.presentation.theme.LukiaMotion
import io.lukia.shared.presentation.theme.millis
import io.lukia.shared.presentation.theme.softFadeOut
import io.lukia.shared.presentation.theme.stateSwapIn
import kotlinx.coroutines.delay

/**
 * Renders a [UiState] the one way the app renders them: [LoadingState] while
 * loading, [ErrorState] on failure, and [content] once there is something to
 * show.
 *
 * Screens call this instead of branching on the state themselves, which is what
 * keeps a failure on the detail screen looking like a failure on the library
 * screen. Anything specific to a screen - an empty state, chrome that floats
 * over the content - belongs inside [content].
 *
 * [actionLabel] and [onAction] are the way out of the failure, and are worth
 * passing whenever there is one: a retry for something that might succeed on a
 * second attempt, a way back for something that will not.
 */
@Composable
fun <T> UiStateContent(
    state: UiState<T>,
    modifier: Modifier = Modifier,
    loadingMessage: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable (T) -> Unit,
) {
    // Nothing at all until the screen has something worth drawing - see
    // [afterFirstWait]. Composing the swap below only from that point is what
    // makes a fast arrival the first thing this ever draws, and so something it
    // draws rather than animates.
    val shown = state.afterFirstWait() ?: run {
        Box(modifier = modifier)
        return
    }

    AnimatedContent(
        targetState = shown,
        modifier = modifier,
        // Cross-dissolve rather than a hard swap, matching how the library
        // already moves between its own states. `using null` keeps
        // AnimatedContent from animating the container's bounds and sliding the
        // content sideways mid-transition.
        transitionSpec = { (stateSwapIn() togetherWith softFadeOut()) using null },
        // Only the case matters for the transition: a Success whose data
        // changed is the same screen, and re-running the fade on every library
        // reload would make the grid blink.
        contentKey = { current ->
            when (current) {
                is UiState.Loading -> "loading"
                is UiState.Failure -> "failure"
                is UiState.Success -> "success"
            }
        },
        label = "uiState",
    ) { current ->
        when (current) {
            is UiState.Loading -> LoadingState(
                message = loadingMessage,
                modifier = Modifier.fillMaxSize(),
            )

            is UiState.Failure -> ErrorState(
                title = current.error.displayTitle(),
                message = current.error.displayMessage(),
                actionLabel = actionLabel,
                onAction = onAction,
                modifier = Modifier.fillMaxSize(),
            )

            is UiState.Success -> content(current.data)
        }
    }
}

/**
 * This state, or null while the screen is still in a first wait short enough
 * not to be worth mentioning.
 *
 * A screen is usually waiting underneath a screen change: a book opened from the
 * shelf arrives on a push. One that opens in eighty milliseconds used to show a
 * spinner and cross-fade it away halfway through that push - three movements
 * over each other, and which of them the reader caught depended on how fast the
 * file opened. Held back for the length of the push instead, a book that opens
 * quickly is simply there when the screen lands, and one that does not gets a
 * spinner once the screen is still enough to read it on.
 *
 * Only the first wait. Once a screen has drawn anything it keeps drawing:
 * blanking a page somebody is already looking at because it is being read again
 * would be worse than the spinner this avoids.
 */
@Composable
private fun <T> UiState<T>.afterFirstWait(): UiState<T>? {
    val motion = LocalMotionStyle.current

    var waitedOut by remember { mutableStateOf(false) }
    var spoken by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(motion.millis(LukiaMotion.LargeMillis).toLong())
        waitedOut = true
    }

    val speaking = spoken || waitedOut || this !is UiState.Loading

    // Latched in an effect rather than written during composition, so that
    // "this screen has said something" is recorded once it actually has.
    LaunchedEffect(speaking) {
        if (speaking) spoken = true
    }

    return if (speaking) this else null
}
