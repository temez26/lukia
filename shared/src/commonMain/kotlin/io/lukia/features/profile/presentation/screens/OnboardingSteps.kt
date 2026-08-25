package io.lukia.features.profile.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.profile.domain.UsernameProblem
import io.lukia.features.profile.domain.Usernames
import io.lukia.features.profile.presentation.components.ProfileAvatar
import io.lukia.features.profile.presentation.components.UsernameField
import io.lukia.shared.presentation.components.LukiaMark
import io.lukia.shared.presentation.theme.Space

/** The mark on the welcome step, sized as the thing the screen is about. */
private val WelcomeMarkSize: Dp = 96.dp

/**
 * The three things a new reader is taken through, in order.
 *
 * Three and no more. Onboarding is a toll on the way to the first book, and
 * every step of it has to earn its place: one to say what this is, one to ask
 * the only question the app has, and one to say it is done. Everything else the
 * app can do - its themes, its translators, its import - is discoverable from
 * the library and is not worth a slide here.
 *
 * [Ready] exists rather than the name step simply finishing because the profile
 * is written once, at the end: a reader who backs out of naming themselves has
 * not created anything, and the last step is where the name they chose is shown
 * back to them before it is.
 */
internal enum class OnboardingStep {
    Welcome,
    Name,
    Ready,
    ;

    /** The step before this one, and itself for the first - see `OnboardingControls`. */
    fun previous(): OnboardingStep = when (this) {
        Welcome -> Welcome
        Name -> Welcome
        Ready -> Name
    }

    /** What the button under the step says, which is what pressing it does. */
    val action: String
        get() = when (this) {
            Welcome -> "Get started"
            Name -> "Continue"
            Ready -> "Start reading"
        }
}

/**
 * What this app is, in the fewest words that answer it.
 *
 * The mark is the same one the app opens with, drawn fanned rather than
 * animating: the launch has just played it, and playing it again here would read
 * as the app starting twice.
 */
@Composable
internal fun WelcomeStep(modifier: Modifier = Modifier) {
    StepColumn(modifier = modifier) {
        LukiaMark(modifier = Modifier.size(WelcomeMarkSize))

        StepTitle(text = "Welcome to Lukia", modifier = Modifier.padding(top = Space.xxl))

        StepDetail(
            text = "Your library, your reading, and translations when you want them - " +
                "on your own device.",
        )
    }
}

/**
 * The one question the app asks, and the reassurance that goes with it.
 *
 * A name and nothing else. There is no email here and no provider to choose,
 * because nothing in this app needs to know who anybody is - the name is for the
 * app to greet its reader by, and saying so is what stops the field reading as
 * the first half of a sign-up.
 */
@Composable
internal fun NameStep(
    username: String,
    onUsernameChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    /** Shown only once the reader has tried to move on - see `OnboardingScreen`. */
    problem: UsernameProblem? = null,
) {
    StepColumn(modifier = modifier) {
        StepTitle(text = "What should we call you?")

        StepDetail(
            text = "Just a name for your profile. No email, no account, " +
                "and you can change it whenever you like.",
        )

        UsernameField(
            value = username,
            onValueChange = onUsernameChange,
            problem = problem,
            onSubmit = onSubmit,
            modifier = Modifier.padding(top = Space.xxl),
        )
    }
}

/**
 * The name, on the avatar it will be shown on from now on.
 *
 * Ending on what was just made rather than on a tick: the monogram here is the
 * one the reader will find in their profile, so the last thing onboarding does
 * is teach them what to look for.
 */
@Composable
internal fun ReadyStep(
    username: String,
    modifier: Modifier = Modifier,
) {
    StepColumn(modifier = modifier) {
        ProfileAvatar(initials = Usernames.initialsOf(username))

        StepTitle(
            text = "You're all set, ${Usernames.clean(username)}",
            modifier = Modifier.padding(top = Space.xxl),
        )

        StepDetail(
            text = "Import a book and your reading builds up on your profile as you go.",
        )
    }
}

/** The one column every step is laid out in, so none of them drifts from the rest. */
@Composable
private fun StepColumn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth(),
        content = { content() },
    )
}

@Composable
private fun StepTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

@Composable
private fun StepDetail(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.padding(top = Space.lg),
    )
}
