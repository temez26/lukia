package io.lukia.features.profile.presentation.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.profile.domain.UsernameProblem
import io.lukia.features.profile.domain.Usernames
import io.lukia.shared.presentation.components.ButtonVariant
import io.lukia.shared.presentation.components.LukiaButton
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.LukiaAlpha
import io.lukia.shared.presentation.theme.Space
import io.lukia.shared.presentation.theme.softFadeOut
import io.lukia.shared.presentation.theme.stateSwapIn

/** The dot marking one step along the bottom of the screen. */
private val StepDotSize: Dp = 7.dp

/**
 * What a new reader sees before they see the app.
 *
 * Shown instead of the shells rather than over them - see `App` - because until
 * there is a profile there is nothing behind this worth a glimpse of, and a
 * library sliding away under a welcome is the app starting twice.
 *
 * Nothing here reaches a store. The name is held in this composition until the
 * last step, and [onFinish] is the single write that creates the profile, so a
 * reader who closes the app halfway through starts again rather than finding a
 * half-made profile named after two letters.
 *
 * There is deliberately no way to skip. It is one question, the answer costs
 * nothing to change afterwards, and a profile with no name is a screen that
 * cannot say who its statistics belong to.
 */
@Composable
fun OnboardingScreen(
    onFinish: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by rememberSaveable { mutableStateOf(OnboardingStep.Welcome) }
    var username by rememberSaveable { mutableStateOf("") }

    // Held back until the reader has actually tried to move on. A field that
    // objects to a name being one character long, while it is being typed, is
    // complaining about work in progress.
    var showProblem by rememberSaveable { mutableStateOf(false) }

    val problem: UsernameProblem? = Usernames.problemWith(username)

    val onwards: () -> Unit = {
        when (step) {
            OnboardingStep.Welcome -> step = OnboardingStep.Name

            OnboardingStep.Name -> if (problem == null) {
                showProblem = false
                step = OnboardingStep.Ready
            } else {
                showProblem = true
            }

            OnboardingStep.Ready -> onFinish(username)
        }
    }

    OnboardingContent(
        step = step,
        username = username,
        onUsernameChange = { username = it },
        onBack = { step = step.previous() },
        onwards = onwards,
        problem = problem.takeIf { showProblem },
        modifier = modifier,
    )
}

/**
 * Stateless half of the screen, so the steps can be composed without any of the
 * state machine above them. Every screen in the app splits this way.
 */
@Composable
internal fun OnboardingContent(
    step: OnboardingStep,
    username: String,
    onUsernameChange: (String) -> Unit,
    onBack: () -> Unit,
    onwards: () -> Unit,
    modifier: Modifier = Modifier,
    problem: UsernameProblem? = null,
) {
    val dimens = LocalLukiaDimens.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .widthIn(max = dimens.maxDetailWidth)
                .padding(horizontal = dimens.screenPadding, vertical = Space.xxl),
        ) {
            // The step takes the room and the controls take what is left, so the
            // buttons sit at the bottom of the window rather than under whichever
            // step happens to be tallest.
            AnimatedContent(
                targetState = step,
                transitionSpec = { (stateSwapIn() togetherWith softFadeOut()) using null },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "onboardingStep",
            ) { shown ->
                OnboardingStepContent(
                    step = shown,
                    username = username,
                    onUsernameChange = onUsernameChange,
                    onwards = onwards,
                    problem = problem,
                )
            }

            StepDots(step = step, modifier = Modifier.padding(bottom = Space.xl))

            OnboardingControls(step = step, onBack = onBack, onwards = onwards)
        }
    }
}

/** Which step is showing, and nothing else. */
@Composable
private fun OnboardingStepContent(
    step: OnboardingStep,
    username: String,
    onUsernameChange: (String) -> Unit,
    onwards: () -> Unit,
    modifier: Modifier = Modifier,
    problem: UsernameProblem? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        when (step) {
            OnboardingStep.Welcome -> WelcomeStep()

            OnboardingStep.Name -> NameStep(
                username = username,
                onUsernameChange = onUsernameChange,
                // The keyboard's Done key does what the button under it does,
                // so a reader who finishes typing never has to reach past it.
                onSubmit = onwards,
                problem = problem,
            )

            OnboardingStep.Ready -> ReadyStep(username = username)
        }
    }
}

/**
 * The way onward, and the way back to the start.
 *
 * Back steps one at a time, so pressing it on the last step is a way to change
 * the name just typed rather than a trip back to the introduction. It is absent
 * on the first step, where there is nowhere to go.
 */
@Composable
private fun OnboardingControls(
    step: OnboardingStep,
    onBack: () -> Unit,
    onwards: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth(),
    ) {
        LukiaButton(
            label = step.action,
            onClick = onwards,
            modifier = Modifier.fillMaxWidth(),
        )

        if (step != OnboardingStep.Welcome) {
            LukiaButton(
                label = "Back",
                onClick = onBack,
                variant = ButtonVariant.Ghost,
                modifier = Modifier.padding(top = Space.md),
            )
        }
    }
}

/** How far along the reader is, as three dots rather than as "2 of 3". */
@Composable
private fun StepDots(
    step: OnboardingStep,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        modifier = modifier,
    ) {
        OnboardingStep.entries.forEach { entry ->
            val here = entry == step

            Box(
                modifier = Modifier
                    .size(StepDotSize)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(
                            alpha = if (here) 1f else LukiaAlpha.Disabled,
                        ),
                    ),
            )
        }
    }
}
