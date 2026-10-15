package io.lukia.features.sync

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.features.sync.presentation.HistoryQuestionContent
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class HistoryQuestionTest {

    @Test
    fun eitherAnswerIsOneTapAway() = runComposeUiTest {
        val answers = mutableListOf<Boolean>()
        setContent {
            LukiaTheme(darkTheme = false) {
                HistoryQuestionContent(onAnswer = { answers += it }, groupColor = MaterialTheme.colorScheme.surface)
            }
        }

        onNodeWithText("Reading on this device").assertExists()
        onNodeWithText("Keep on this device").performClick()
        onNodeWithText("Add to my account").performClick()

        assertEquals(listOf(false, true), answers)
    }
}
