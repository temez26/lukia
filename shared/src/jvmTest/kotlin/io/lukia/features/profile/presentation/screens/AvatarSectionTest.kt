package io.lukia.features.profile.presentation.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.features.profile.domain.ProfileAvatar
import io.lukia.features.profile.domain.ProfilePicture
import io.lukia.features.profile.domain.ProfileSymbol
import io.lukia.features.profile.domain.ProfileTint
import io.lukia.features.profile.presentation.components.ProfileFace
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Choosing what the reader's disc shows. */
@OptIn(ExperimentalTestApi::class)
class AvatarSectionTest {

    private var chosenAvatar: ProfileAvatar? = null
    private var chosenTint: ProfileTint? = null
    private var pickedPicture = false
    private var removedPicture = false

    private fun ComposeUiTest.setAvatarSection(face: ProfileFace = ProfileFace(initials = "AL")) {
        setContent {
            LukiaTheme(darkTheme = false) {
                AvatarSection(
                    face = face,
                    actions = AvatarActions(
                        onAvatar = { chosenAvatar = it },
                        onTint = { chosenTint = it },
                        onChoosePicture = { pickedPicture = true },
                        onRemovePicture = { removedPicture = true },
                    ),
                    groupColor = MaterialTheme.colorScheme.surface,
                )
            }
        }
    }

    @Test
    fun everyMarkTheAppShipsIsOffered() = runComposeUiTest {
        setAvatarSection()

        onNodeWithContentDescription("Your initials").assertIsDisplayed()

        ProfileSymbol.entries.forEach { symbol ->
            onNodeWithContentDescription(symbol.title).assertIsDisplayed()
        }
    }

    @Test
    fun choosingAMarkReportsIt() = runComposeUiTest {
        setAvatarSection()

        onNodeWithContentDescription(ProfileSymbol.Feather.title).performClick()

        assertEquals(ProfileAvatar.Symbol(ProfileSymbol.Feather), chosenAvatar)
    }

    @Test
    fun theInitialsAreAChoiceLikeAnyOther() = runComposeUiTest {
        setAvatarSection(ProfileFace(initials = "AL", avatar = ProfileAvatar.Symbol(ProfileSymbol.Moon)))

        onNodeWithContentDescription("Your initials").performClick()

        assertEquals(ProfileAvatar.Monogram, chosenAvatar)
    }

    /** The swatches carry their names for anything that cannot see a colour. */
    @Test
    fun choosingAColourReportsIt() = runComposeUiTest {
        setAvatarSection()

        onNodeWithContentDescription(ProfileTint.Green.title).performClick()

        assertEquals(ProfileTint.Green, chosenTint)
    }

    @Test
    fun aReaderWithNoPictureIsOfferedOne() = runComposeUiTest {
        setAvatarSection()

        onNodeWithText("Choose a picture").performClick()

        assertTrue(pickedPicture)
    }

    /** Nothing to remove is nothing to offer removing. */
    @Test
    fun theWayToRemoveAPictureIsOnlyThereWhenThereIsOne() = runComposeUiTest {
        setAvatarSection()

        onNodeWithText("Remove").assertDoesNotExist()
    }

    @Test
    fun aPictureCanBeTakenOffAgain() = runComposeUiTest {
        setAvatarSection(
            ProfileFace(
                initials = "AL",
                avatar = ProfileAvatar.Picture,
                picture = ProfilePicture(byteArrayOf(1, 2, 3)),
            ),
        )

        onNodeWithText("Change picture").assertIsDisplayed()
        onNodeWithText("Remove").performClick()

        assertTrue(removedPicture)
    }
}
