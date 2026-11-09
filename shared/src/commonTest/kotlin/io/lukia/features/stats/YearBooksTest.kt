package io.lukia.features.stats

import io.lukia.core.domain.reading.ReadingSession
import io.lukia.features.library.TestBook
import io.lukia.features.library.book
import io.lukia.features.library.finished
import io.lukia.features.library.partRead
import io.lukia.features.stats.domain.yearBooks
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** This year's books: where the time went and what was finished, on 11 March 2026. */
class YearBooksTest {

    private val zone = TimeZone.UTC
    private val today = LocalDate(2026, Month.MARCH, 11)

    private fun sitting(book: String, on: LocalDate, minutes: Int, title: String? = book) = ReadingSession(
        location = "/library/$book.pdf",
        startedAt = LocalDateTime(on, LocalTime(12, 0)).toInstant(zone).toEpochMilliseconds(),
        durationMs = minutes * 60_000L,
        title = title,
    )

    private fun booksOf(shelf: List<TestBook>, vararg sittings: ReadingSession) = yearBooks(
        sessions = sittings.toList(),
        documents = shelf.map(TestBook::document),
        progress = shelf.mapNotNull { entry -> entry.progress?.let { entry.document.document.location to it } }.toMap(),
        today = today,
        zone = zone,
    )

    @Test
    fun mostReadIsOrderedByTimeSpentThisYear() {
        val books = booksOf(
            listOf(book("Short"), book("Long")),
            sitting("Short", LocalDate(2026, Month.JANUARY, 3), 20),
            sitting("Long", LocalDate(2026, Month.FEBRUARY, 3), 90),
            sitting("Short", LocalDate(2025, Month.DECEMBER, 30), 600),
        )

        assertEquals(listOf("Long", "Short"), books.mostRead.map { it.title })
        assertEquals(20 * 60_000L, books.mostRead.last().durationMs)
    }

    @Test
    fun aBookNoLongerOnTheShelfKeepsItsTitleButHasNoDocument() {
        val gone = booksOf(emptyList(), sitting("Gone", LocalDate(2026, Month.MARCH, 1), 30)).mostRead.single()

        assertEquals("Gone", gone.title)
        assertNull(gone.document)
    }

    @Test
    fun mostReadIsCappedAtFive() {
        val sittings = (1..8).map { sitting("Book $it", LocalDate(2026, Month.MARCH, it), it) }

        assertEquals(5, booksOf(emptyList(), *sittings.toTypedArray()).mostRead.size)
    }

    @Test
    fun aFinishedBookIsDatedByItsLastSitting() {
        val books = booksOf(
            listOf(book("Done", progress = finished())),
            sitting("Done", LocalDate(2026, Month.JANUARY, 5), 30),
            sitting("Done", LocalDate(2026, Month.FEBRUARY, 9), 30),
        )

        assertEquals(LocalDate(2026, Month.FEBRUARY, 9), books.finished.single().finishedOn)
    }

    @Test
    fun finishedBooksAreNewestFirst() {
        val books = booksOf(
            listOf(book("First", progress = finished()), book("Second", progress = finished())),
            sitting("First", LocalDate(2026, Month.JANUARY, 5), 30),
            sitting("Second", LocalDate(2026, Month.MARCH, 2), 30),
        )

        assertEquals(
            listOf("/library/Second.pdf", "/library/First.pdf"),
            books.finished.map {
                it.document.document.location
            },
        )
    }

    @Test
    fun onlyBooksFinishedThisYearAndRead() {
        val books = booksOf(
            listOf(
                book("Last year", progress = finished()),
                book("Never timed", progress = finished()),
                book("Still going", progress = partRead()),
            ),
            sitting("Last year", LocalDate(2025, Month.NOVEMBER, 5), 30),
            sitting("Still going", LocalDate(2026, Month.MARCH, 5), 30),
        )

        assertTrue(books.finished.isEmpty())
    }
}
