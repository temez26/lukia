package io.lukia.features.settings.presentation.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.LukiaVersion
import io.lukia.features.settings.domain.LukiaAttributions
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test

/**
 * The two questions About exists to answer, asked of the group itself.
 *
 * `LukiaVersion` reached a diagnostic report and a crash note long before it
 * reached a screen, so the version being on the page is the whole of the first
 * one - and it is asserted against the generated constant rather than against a
 * number written here, because a test that agreed with itself about the version
 * would pass through a release that forgot to change it.
 *
 * The second is the licence list, which is what Apache-2.0 section 4 asks a
 * distribution to carry. It is folded away until it is asked for, so both halves
 * of that are worth holding: nobody reading Settings has seventeen licences put
 * in front of them, and nobody looking for one has to leave the app.
 */
@OptIn(ExperimentalTestApi::class)
class AboutSettingsTest {

    /**
     * A plain colour rather than the theme's, because what a group sits on is the
     * page's decision and none of these tests is about it - see `SettingsBody`.
     */
    private fun ComposeUiTest.setAbout() {
        setContent { LukiaTheme(darkTheme = false) { AboutSettings(groupColor = Color.White) } }
    }

    @Test
    fun theVersionIsOnThePage() = runComposeUiTest {
        setAbout()

        onNodeWithText(LukiaVersion, substring = true).assertExists()
    }

    @Test
    fun theLicencesAreFoldedAwayUntilAskedFor() = runComposeUiTest {
        setAbout()

        onNodeWithText("Apache PDFBox").assertDoesNotExist()

        onNodeWithText("Show").performClick()

        onNodeWithText("Apache PDFBox").assertExists()
    }

    /**
     * Every one of them, rather than the first: a licence notice carrying most of
     * what a distribution ships is the failure this screen exists to prevent, and
     * it is the sort that goes unnoticed because the screen still looks right.
     *
     * Through `onAllNodesWithText` because a copyright line is not unique - three
     * of the dependencies are JetBrains' and two are Square's, and each says so in
     * the same words.
     */
    @Test
    fun eachDependencyArrivesWithItsNameAndItsCopyright() = runComposeUiTest {
        setAbout()

        onNodeWithText("Show").performClick()

        val expected = LukiaAttributions.flatMap { listOf(it.name, it.copyright) }.distinct()

        expected.forEach { onAllNodesWithText(it).onFirst().assertExists() }
    }
}
