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
 * German, Dutch and French, and the three places they disagree with each other.
 *
 * They are grouped because each one breaks a rule the Nordic set made look
 * universal: Dutch writes no space before the percent sign, French counts a
 * nought as singular, and French measures in octets rather than bytes.
 */
class WesternStringsTest {

    private val wasDefault: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(wasDefault)

    @Test
    fun eachLanguageSaysItsOwnWords() {
        mapOf("de" to "Bibliothek", "nl" to "Bibliotheek", "fr" to "Bibliothèque")
            .forEach { (tag, library) ->
                Locale.setDefault(Locale.forLanguageTag(tag))

                assertEquals(library, runBlocking { getString(Res.string.nav_library) }, tag)
            }
    }

    /**
     * The Nordic languages and German put a space before the percent sign;
     * Dutch does not. A resource is the only place that difference can live.
     */
    @Test
    fun dutchWritesNoSpaceBeforeThePercentSign() {
        Locale.setDefault(Locale.GERMAN)
        assertEquals("125 %", runBlocking { getString(Res.string.reader_width_percent, 125) })

        Locale.setDefault(Locale.forLanguageTag("nl"))
        assertEquals("125%", runBlocking { getString(Res.string.reader_width_percent, 125) })
    }

    /**
     * French counts nought as singular - `one` covers 0 and 1 - which is why its
     * singular form carries the number rather than writing "1" into the text.
     * An `if (n == 1)` in the code could never have expressed this.
     */
    @Test
    fun frenchCountsNoughtAsSingular() = runBlocking {
        Locale.setDefault(Locale.FRENCH)

        assertEquals("0 page", getPluralString(Res.plurals.book_length_pages, 0, 0))
        assertEquals("1 page", getPluralString(Res.plurals.book_length_pages, 1, 1))
        assertEquals("2 pages", getPluralString(Res.plurals.book_length_pages, 2, 2))
    }

    /** German counts a nought as plural, which is the ordinary case. */
    @Test
    fun germanCountsNoughtAsPlural() = runBlocking {
        Locale.setDefault(Locale.GERMAN)

        assertEquals("0 Seiten", getPluralString(Res.plurals.book_length_pages, 0, 0))
        assertEquals("1 Seite", getPluralString(Res.plurals.book_length_pages, 1, 1))
    }

    /** And French measures files in octets, which is what the unit resource is for. */
    @Test
    fun frenchMeasuresInOctets() {
        Locale.setDefault(Locale.FRENCH)
        assertEquals("1,5 ko", composed { formatFileSize(1536L) })

        Locale.setDefault(Locale.GERMAN)
        assertEquals("1,5 kB", composed { formatFileSize(1536L) })
    }
}
