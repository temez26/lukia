package io.lukia.shared.presentation.components.menu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/** The book menu on a phone: a sheet from the bottom, the book on top, the common four as buttons. */
@OptIn(ExperimentalTestApi::class)
class BookActionsSheetTest {

    private var favourited = false
    private var marked = false

    private fun ComposeUiTest.showOnPhone(onMarkUnread: (() -> Unit)? = { marked = true }) {
        setContent {
            var expanded by remember { mutableStateOf(true) }

            // A phone's width, which is what decides between the sheet and the anchored menu.
            Box(modifier = Modifier.size(400.dp, 800.dp)) {
                LukiaTheme(darkTheme = false) {
                    BookActionsMenu(
                        expanded = expanded,
                        isFavorite = false,
                        onToggleFavorite = { favourited = true },
                        onRefresh = {},
                        onDelete = {},
                        onDismissRequest = { expanded = false },
                        onShowDetails = {},
                        onMarkUnread = onMarkUnread,
                        header = BookMenuHeader(
                            title = "Piranesi",
                            author = "Susanna Clarke",
                            cover = null,
                            percent = 42,
                        ),
                    )
                }
            }
        }
    }

    @Test
    fun theSheetNamesTheBookAndOffersTheCommonChoicesAsButtons() = runComposeUiTest {
        showOnPhone()

        onNodeWithText("Piranesi").assertExists()
        onNodeWithText("Susanna Clarke · 42%").assertExists()
        onNodeWithContentDescription("Add to Favourites").assertExists()
        onNodeWithContentDescription("Mark as Unread").assertExists()
        onNodeWithText("Refresh Details").assertExists()
        onNodeWithText("Remove from Device").assertExists()
    }

    /** The sheet slides away first and acts once it has gone. */
    @Test
    fun aChoiceActsAndClosesTheSheet() = runComposeUiTest {
        showOnPhone()

        onNodeWithContentDescription("Add to Favourites").performClick()
        waitForIdle()

        assertTrue(favourited)
        onNodeWithText("Piranesi").assertDoesNotExist()
    }

    @Test
    fun aChoiceTheCallerDoesNotOfferHasNoButton() = runComposeUiTest {
        showOnPhone(onMarkUnread = null)

        onNodeWithContentDescription("Mark as Unread").assertDoesNotExist()
    }
}
