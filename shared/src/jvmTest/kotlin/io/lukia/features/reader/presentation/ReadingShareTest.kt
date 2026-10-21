package io.lukia.features.reader.presentation

import io.lukia.core.domain.reading.ReadingProgress
import kotlin.test.Test
import kotlin.test.assertEquals

/** The reader's percentage, which has to be the one the library shows for the same page. */
class ReadingShareTest {

    @Test
    fun itAgreesWithTheLibrary() {
        for (page in 1..300) {
            val library = ReadingProgress(pageIndex = page - 1, pageCount = 300).percent

            assertEquals(library, percentThrough(page, 300), "page $page")
        }
    }

    @Test
    fun aPageShortOfTheEndIsNotAllOfIt() {
        assertEquals(99, percentThrough(299, 300))
        assertEquals(100, percentThrough(300, 300))
    }

    @Test
    fun theFirstPageOfALongBookIsAPercentIn() {
        assertEquals(1, percentThrough(1, 1000))
    }
}
