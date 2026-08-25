package io.lukia.features.profile.presentation.screens

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The first thing a new reader ever sees, walked the way they walk it.
 *
 * The whole flow is driven through the real screen rather than through the step
 * enum behind it, because what is worth pinning is that a profile is created
 * once, at the end, and only from a name the app will accept.
 */
@OptIn(ExperimentalTestApi::class)
class OnboardingFlowTest {

    private var created: String? = null

    private fun ComposeUiTest.setOnboarding() {
        setContent {
            LukiaTheme(darkTheme = false) {
                OnboardingScreen(onFinish = { created = it })
            }
        }
    }

    private fun ComposeUiTest.type(name: String) {
        onNodeWithContentDescription("Your name").performTextInput(name)
    }

    @Test
    fun itOpensOnTheWelcome() = runComposeUiTest {
        setOnboarding()

        onNodeWithText("Welcome to Lukia").assertIsDisplayed()
    }

    @Test
    fun theOnlyThingItAsksForIsAName() = runComposeUiTest {
        setOnboarding()

        onNodeWithText("Get started").performClick()

        onNodeWithText("What should we call you?").assertIsDisplayed()
        onNodeWithContentDescription("Your name").assertIsDisplayed()
    }

    @Test
    fun aNameCarriesThroughToTheLastStep() = runComposeUiTest {
        setOnboarding()

        onNodeWithText("Get started").performClick()
        type("Ada")
        onNodeWithText("Continue").performClick()

        onNodeWithText("You're all set, Ada").assertIsDisplayed()
    }

    /** The profile is one write, at the end, and only then. */
    @Test
    fun theProfileIsCreatedOnlyOnTheLastStep() = runComposeUiTest {
        setOnboarding()

        onNodeWithText("Get started").performClick()
        type("Ada")
        onNodeWithText("Continue").performClick()

        assertNull(created)

        onNodeWithText("Start reading").performClick()

        assertEquals("Ada", created)
    }

    @Test
    fun aNameTooShortToUseKeepsTheReaderOnTheQuestion() = runComposeUiTest {
        setOnboarding()

        onNodeWithText("Get started").performClick()
        type("A")
        onNodeWithText("Continue").performClick()

        onNodeWithText("What should we call you?").assertIsDisplayed()
        onNodeWithText("A little longer - at least 2 letters.").assertIsDisplayed()
    }

    /** Nothing is said about a name until somebody has finished typing one. */
    @Test
    fun theFieldDoesNotComplainWhileItIsBeingTypedIn() = runComposeUiTest {
        setOnboarding()

        onNodeWithText("Get started").performClick()
        type("A")

        onNodeWithText("A little longer - at least 2 letters.").assertDoesNotExist()
    }

    @Test
    fun backGoesToTheStepBeforeRatherThanToTheStart() = runComposeUiTest {
        setOnboarding()

        onNodeWithText("Get started").performClick()
        type("Ada")
        onNodeWithText("Continue").performClick()
        onNodeWithText("Back").performClick()

        onNodeWithText("What should we call you?").assertIsDisplayed()
    }

    @Test
    fun thereIsNoWayPastTheQuestionWithoutAnsweringIt() = runComposeUiTest {
        setOnboarding()

        onNodeWithText("Get started").performClick()

        onNodeWithText("Skip").assertDoesNotExist()
    }
}
