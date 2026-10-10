package io.lukia.features.settings.presentation.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.LukiaVersion
import io.lukia.features.settings.domain.LukiaAttributions
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The three questions About exists to answer, asked of the group itself.
 *
 * The first is the version, asserted against the generated constant rather than
 * a number written here: a test that agreed with itself about the version would
 * pass through a release that forgot to change it.
 *
 * The second is the licence list, which Apache-2.0 section 4 asks a distribution
 * to carry. It is behind a mark until asked for, so both halves are worth
 * holding: nobody reading Settings has seventeen licences put in front of them,
 * and nobody looking for one has to leave the app.
 *
 * The third is the way in to the developer settings, which is the version line
 * and nothing else - so the count that opens them is worth pinning down, since
 * nothing on the page says it is there.
 */
@OptIn(ExperimentalTestApi::class)
class AboutSettingsTest {

    /** What the group last asked for, which is the whole of what it reports. */
    private var unlocked: Boolean? = null

    /**
     * A plain colour rather than the theme's, because what a group sits on is the
     * page's decision and none of these tests is about it - see `SettingsBody`.
     */
    private fun ComposeUiTest.setAbout(developerMode: Boolean = false) {
        setContent {
            LukiaTheme(darkTheme = false) {
                AboutSettings(
                    groupColor = Color.White,
                    developerMode = developerMode,
                    onDeveloperModeChange = { unlocked = it },
                )
            }
        }
    }

    private fun ComposeUiTest.openTheLicences() {
        onNodeWithContentDescription(ExplainLicences).performClick()
    }

    @Test
    fun theVersionIsOnThePage() = runComposeUiTest {
        setAbout()

        onNodeWithText(LukiaVersion, substring = true).assertExists()
    }

    @Test
    fun theLicencesAreBehindTheMarkUntilAskedFor() = runComposeUiTest {
        setAbout()

        onNodeWithText("Apache PDFBox").assertDoesNotExist()

        openTheLicences()

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

        openTheLicences()

        val expected = LukiaAttributions.flatMap { listOf(it.name, it.copyright) }.distinct()

        expected.forEach { onAllNodesWithText(it).onFirst().assertExists() }
    }

    /**
     * Six presses is a reader who found the version interesting; the seventh is
     * somebody being talked through it. Nothing happens before it.
     */
    @Test
    fun theDeveloperSettingsTakeSevenPressesOnTheVersion() = runComposeUiTest {
        setAbout()

        val version = onNodeWithText(LukiaVersion, substring = true)

        repeat(6) { version.performClick() }

        assertNull(unlocked)

        version.performClick()

        assertEquals(true, unlocked)
    }

    /**
     * The switch that appears is the way back out. Without it the only way to
     * put the diagnostics away again would be the gesture that revealed them,
     * which does not toggle.
     */
    @Test
    fun theyAreTurnedOffFromTheSwitchTheyPutThere() = runComposeUiTest {
        setAbout(developerMode = true)

        onNodeWithContentDescription(DeveloperOptions).performClick()

        assertEquals(false, unlocked)
    }

    private companion object {
        /** As `action_explain` and `settings_open_source` name it together. */
        const val ExplainLicences = "More about Open-source licences"

        /** The switch's own name, which `toggleRow` is what puts on the row. */
        const val DeveloperOptions = "Developer options"
    }
}
