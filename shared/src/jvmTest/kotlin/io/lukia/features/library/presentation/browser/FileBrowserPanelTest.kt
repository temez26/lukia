package io.lukia.features.library.presentation.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.lukia.core.domain.document.PickedDocument
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlinx.coroutines.Dispatchers
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class FileBrowserPanelTest {

    private fun folderWith(vararg names: String): File {
        val root = createTempDirectory("panel").toFile()

        names.forEach { name ->
            if (name.endsWith('/')) File(root, name.trimEnd('/')).mkdirs() else File(root, name).writeText("")
        }

        return root
    }

    @AfterTest
    fun clearPending() {
        DesktopPicks.cancel()
    }

    @Test
    fun `lists what is in the folder it opens at`() = runComposeUiTest {
        val folder = folderWith("Dune.epub", "Papers/")
        DesktopPicks.remember(folder)

        setContent {
            LukiaTheme {
                Box(modifier = Modifier.size(900.dp, 900.dp)) {
                    FileBrowserPanel(DesktopPick.Books {}, dispatcher = Dispatchers.Unconfined)
                }
            }
        }

        waitForIdle()

        onNodeWithText("Dune.epub").assertIsDisplayed()
        onNodeWithText("Papers").assertIsDisplayed()
        onNodeWithText("Add books to the library").assertIsDisplayed()
    }

    @Test
    fun `picking books counts them and hands back what was picked`() = runComposeUiTest {
        val folder = folderWith("Dune.epub", "Ubik.epub")
        DesktopPicks.remember(folder)

        var picked: List<PickedDocument>? = null
        DesktopPicks.ask(DesktopPick.Books { picked = it })

        setContent {
            LukiaTheme {
                Box(modifier = Modifier.size(900.dp, 900.dp)) {
                    DesktopPicks.pending?.let { FileBrowserPanel(it, dispatcher = Dispatchers.Unconfined) }
                }
            }
        }

        waitForIdle()

        // Nothing picked yet, so there is nothing to confirm.
        onNodeWithText("Add books").assertIsNotEnabled()

        onNodeWithText("Dune.epub").performClick()
        waitForIdle()
        onNodeWithText("Add 1 book").assertIsEnabled()

        onNodeWithText("Ubik.epub").performClick()
        waitForIdle()
        onNodeWithText("Add 2 books").performClick()
        waitForIdle()

        assertEquals(listOf("Dune.epub", "Ubik.epub"), picked?.map { it.fileName }?.sorted())
    }

    @Test
    fun `a folder pick shows no files and can always be confirmed`() = runComposeUiTest {
        val folder = folderWith("Dune.epub", "Papers/")
        DesktopPicks.remember(folder)

        setContent {
            LukiaTheme {
                Box(modifier = Modifier.size(900.dp, 900.dp)) {
                    FileBrowserPanel(DesktopPick.Folder {}, dispatcher = Dispatchers.Unconfined)
                }
            }
        }

        waitForIdle()

        onNodeWithText("Papers").assertIsDisplayed()
        onNodeWithText("Use ${folder.name}").assertIsEnabled()
    }
}
