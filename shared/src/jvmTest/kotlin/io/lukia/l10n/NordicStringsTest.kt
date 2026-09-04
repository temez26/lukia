package io.lukia.l10n

import io.lukia.core.presentation.formatFileSize
import io.lukia.resources.Res
import io.lukia.resources.book_length_pages
import io.lukia.resources.nav_library
import io.lukia.resources.reader_width_percent
import io.lukia.shared.composed
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Swedish, Norwegian and Danish, which share a convention English does not.
 *
 * All three write a space before the percent sign and a comma for the decimal
 * mark, and all three are close enough to each other that the risk is not
 * getting one wrong but copying one into the next. So the words are asserted per
 * language and the number conventions across all of them at once.
 */
class NordicStringsTest {

    private val wasDefault: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(wasDefault)

    @Test
    fun eachLanguageSaysItsOwnWords() {
        val expected = mapOf(
            "sv" to ("Bibliotek" to "348 sidor"),
            "nb" to ("Bibliotek" to "348 sider"),
            "da" to ("Bibliotek" to "348 sider"),
        )

        expected.forEach { (tag, words) ->
            Locale.setDefault(Locale.forLanguageTag(tag))

            val (library, pages) = words
            assertEquals(library, runBlocking { getString(Res.string.nav_library) }, tag)
            assertEquals(
                pages,
                runBlocking { getPluralString(Res.plurals.book_length_pages, 348, 348) },
                tag,
            )
        }
    }

    /**
     * The two things none of them writes the English way, and the two that could
     * not have been fixed anywhere but in a resource and a platform formatter.
     */
    @Test
    fun allOfThemWriteNumbersTheNordicWay() {
        listOf("sv", "nb", "da").forEach { tag ->
            Locale.setDefault(Locale.forLanguageTag(tag))

            assertEquals(
                "125\u00A0%",
                runBlocking { getString(Res.string.reader_width_percent, 125) },
                tag,
            )
            assertEquals("1,5\u00A0kB", composed { formatFileSize(1536L) }, tag)
        }
    }

    /** And English is left as it was, which is what says the others are additions. */
    @Test
    fun englishKeepsItsOwnConventions() {
        Locale.setDefault(Locale.ENGLISH)

        assertEquals("125%", runBlocking { getString(Res.string.reader_width_percent, 125) })
        assertEquals("1.5 KB", composed { formatFileSize(1536L) })
    }
}
