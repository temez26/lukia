package io.lukia.features.library.presentation.components.book

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.PickedDocument
import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.features.library.presentation.model.asLibrary
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The books being read, above a window's shelf. */
@OptIn(ExperimentalTestApi::class)
class ContinueReadingBandTest {

    private fun book(name: String) = DocumentInfo(
        document = PickedDocument("$name.epub", "epub", "/library/$name.epub"),
        type = DocumentType.EPUB,
        exists = true,
        sizeBytes = 1_000L,
    )

    private val documents = listOf("Dune", "Emma", "Ulysses", "Beloved", "Unopened", "Finished").map(::book)

    private val progress = mapOf(
        "/library/Dune.epub" to ReadingProgress(pageIndex = 10, pageCount = 100, readAt = 1),
        "/library/Emma.epub" to ReadingProgress(pageIndex = 10, pageCount = 100, readAt = 4),
        "/library/Ulysses.epub" to ReadingProgress(pageIndex = 10, pageCount = 100, readAt = 3),
        "/library/Beloved.epub" to ReadingProgress(pageIndex = 10, pageCount = 100, readAt = 2),
        "/library/Finished.epub" to ReadingProgress(pageIndex = 99, pageCount = 100, readAt = 5),
    )

    @Test
    fun itOffersTheLastFewBeingReadMostRecentFirst() {
        val offered = documents.asLibrary(progress).continueReading()

        assertEquals(
            listOf("/library/Emma.epub", "/library/Ulysses.epub", "/library/Beloved.epub"),
            offered.map { it.location },
        )
    }

    @Test
    fun pressingABookOpensIt() = runComposeUiTest {
        var opened: DocumentInfo? = null
        val offered = documents.asLibrary(progress).continueReading()

        setContent {
            LukiaTheme(darkTheme = false) {
                ContinueReadingBand(books = offered, onOpen = { opened = it })
            }
        }

        onNodeWithText("Ulysses", substring = true).performClick()

        assertEquals("/library/Ulysses.epub", opened?.document?.location)
    }
}
