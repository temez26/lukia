package io.lukia.features.translate.presentation

import io.lukia.features.translate.domain.TextAnchor
import io.lukia.features.translate.domain.Translation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a session keeps and what it forgets.
 *
 * Passages are held by where they are, only so many at a time, and the one
 * given up is the one the reader has left alone the longest.
 */
class TranslationSessionMemoryTest {

    @Test
    fun passagesAreKeptApartByWhereTheyAre() = runTest {
        val other = TextAnchor.Section(sectionIndex = 0, blockIndex = 1, sentenceIndex = 3)
        val session = session { Translation("Translated ${it.text}") }

        session.tap(anchor, passage = "First.")
        session.tap(other, passage = "Second.")
        runCurrent()

        assertEquals("Translated First.", session.ready(anchor).translation.text)
        assertEquals("Translated Second.", session.ready(other).translation.text)
    }

    /**
     * A page's sentence and a section's sentence carrying the same numbers are
     * not the same sentence.
     */
    @Test
    fun aPageAnchorIsNotASectionAnchor() = runTest {
        val page = TextAnchor.Page(pageIndex = 0, sentenceIndex = 2)
        val session = session { Translation("Translated.") }

        session.tap(page)
        runCurrent()

        assertTrue(session.passages.value.containsKey(page))
        assertNull(session.passages.value[TextAnchor.Section(0, 0, 2)])
    }

    /**
     * A long evening with a long book cannot end with every tap copying every
     * translation made since it was opened.
     */
    @Test
    fun onlySoManyPassagesAreKeptAtOnce() = runTest {
        val session = session { Translation("Translated ${it.text}") }

        repeat(500) { index -> session.tap(anchor(index)) }
        runCurrent()

        assertEquals(400, session.passages.value.size)
    }

    /** What falls out is the part of the book furthest behind the reader. */
    @Test
    fun theOldestPassageIsTheOneForgotten() = runTest {
        val session = session { Translation("Translated ${it.text}") }

        repeat(401) { index -> session.tap(anchor(index)) }
        runCurrent()

        assertNull(session.passages.value[anchor(0)])
        assertIs<PassageTranslation.Ready>(session.passages.value[anchor(400)])
    }

    /** Flipping a passage is touching it, so it is not the next one dropped. */
    @Test
    fun aPassageTheReaderKeepsComingBackToIsKept() = runTest {
        val session = session { Translation("Translated ${it.text}") }

        session.tap(anchor(0))
        runCurrent()

        repeat(399) { index -> session.tap(anchor(index + 1)) }
        runCurrent()

        // Flipping it to the original is a touch, and moves it to the front.
        session.tap(anchor(0))
        session.tap(anchor(500))
        runCurrent()

        assertIs<PassageTranslation.Ready>(session.passages.value[anchor(0)])
        assertNull(session.passages.value[anchor(1)])
    }

    @Test
    fun clearingForgetsEverythingTranslatedSoFar() = runTest {
        val session = session { Translation("Kutsukaa minua Ismaeliksi.") }

        session.tap(anchor)
        runCurrent()
        session.clear()

        assertTrue(session.passages.value.isEmpty())
    }

    /** The request is only built when there is actually something to ask. */
}
