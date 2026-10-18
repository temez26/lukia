package io.lukia.features.reader.presentation.screens

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.PickedDocument
import io.lukia.core.domain.storage.WorkingCopy
import io.lukia.core.presentation.UiState
import io.lukia.features.reader.domain.ReaderDocument
import io.lukia.features.reader.domain.ReflowableContent
import io.lukia.features.reader.domain.text.TextBlock
import io.lukia.features.reader.domain.text.TextRun
import io.lukia.features.reader.domain.text.TextSection
import io.lukia.features.reader.presentation.percentThrough
import kotlin.test.Test
import kotlin.test.assertEquals

/** The bar follows a jump made with the scrubber, not only a scroll by hand. */
@OptIn(ExperimentalTestApi::class)
class ReaderScrubberBarTest {

    @Test
    fun theBarFollowsTheScrubber() = runComposeUiTest {
        setReader(pageCount = 100, outline = chapters)

        openContents(pageCount = 100)
        onNode(hasContentDescription("Go to page", substring = true))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(42f) }
        waitForIdle()

        onNodeWithText("42 / 100").assertExists()
        // The bar's and the scrubber's.
        assertEquals(2, onAllNodesWithText("42%").fetchSemanticsNodes().size)
    }

    @Test
    fun theBarFollowsTheScrubberOnAPhone() = runComposeUiTest {
        setReader(pageCount = 100, outline = chapters, windowWidth = 390.dp)

        openContents(pageCount = 100)
        onNode(hasContentDescription("Go to page", substring = true))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(42f) }
        waitForIdle()

        onNodeWithText("42 / 100").assertExists()
        assertEquals(2, onAllNodesWithText("42%").fetchSemanticsNodes().size)
    }

    /** The same for a book the reader lays out itself, which is where it went stale. */
    @Test
    fun theBarFollowsTheScrubberInAReflowableBook() = runComposeUiTest {
        val sections = (1..40).map { number ->
            TextSection(
                title = "Chapter $number",
                blocks = listOf(TextBlock.Heading(listOf(TextRun("Chapter $number")), level = 1)) +
                    (1..6).map { TextBlock.Paragraph(listOf(TextRun("Words of chapter $number. ".repeat(40)))) },
            )
        }
        val document = ReaderDocument(
            info = DocumentInfo(
                document = PickedDocument(fileName = "long.epub", extension = "epub", location = "/library/long.epub"),
                type = DocumentType.EPUB,
                exists = true,
                sizeBytes = 1024L,
            ),
            workingCopy = WorkingCopy(sourceLocation = "/library/long.epub", path = "/workspace/long.epub"),
            content = ReflowableContent(sections = sections, outline = emptyList()),
        )

        setReader(pageCount = 1, uiState = UiState.Success(document))
        mainClock.advanceTimeBy(5000)
        waitForIdle()

        val count = onNode(hasContentDescription("Page 1 of", substring = true) and hasClickAction())
            .fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
            .substringAfter("Page 1 of ").takeWhile { it.isDigit() }.toInt()

        openContents(pageCount = count)
        onNode(hasContentDescription("Go to page", substring = true))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(count / 2f) }
        waitForIdle()

        val page = count / 2
        val percent = percentThrough(page, count)
        onNode(
            hasContentDescription("Page $page of $count. $percent percent read", substring = true) and hasClickAction(),
        ).assertExists()
        onNodeWithText("$page / $count").assertExists()
        assertEquals(2, onAllNodesWithText("$percent%").fetchSemanticsNodes().size)
    }
}
