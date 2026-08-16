package io.lukia.shared.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.displayMessage
import io.lukia.core.presentation.displayTitle
import io.lukia.shared.presentation.theme.softFadeOut
import io.lukia.shared.presentation.theme.stateSwapIn

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
    content: @Composable (T) -> Unit
) {
    AnimatedContent(
        targetState = state,
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
        label = "uiState"
    ) { current ->
        when (current) {
            is UiState.Loading -> LoadingState(
                message = loadingMessage,
                modifier = Modifier.fillMaxSize()
            )

            is UiState.Failure -> ErrorState(
                title = current.error.displayTitle(),
                message = current.error.displayMessage(),
                actionLabel = actionLabel,
                onAction = onAction,
                modifier = Modifier.fillMaxSize()
            )

            is UiState.Success -> content(current.data)
        }
    }
}
