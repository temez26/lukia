package io.lukia.l10n

import io.lukia.resources.Res
import io.lukia.resources.book_length_pages
import io.lukia.resources.nav_library
import io.lukia.resources.reader_width_percent
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Spanish, Italian and Polish - and Polish is why they are worth a test of
 * their own.
 *
 * Every language before this one had two plural forms, which a boolean can
 * express by accident. Polish has four, and none of them is reachable from
 * `if (n == 1)`. This is the first evidence that moving the counts into plural
 * resources bought something a two-form language could not show.
 */
class SouthernStringsTest {

    private val wasDefault: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(wasDefault)

    @Test
    fun eachLanguageSaysItsOwnWords() {
        mapOf("es" to "Biblioteca", "it" to "Libreria", "pl" to "Biblioteka")
            .forEach { (tag, library) ->
                Locale.setDefault(Locale.forLanguageTag(tag))

                assertEquals(library, runBlocking { getString(Res.string.nav_library) }, tag)
            }
    }

    /**
     * The four Polish forms, at the numbers that select them.
     *
     * `one` for 1, `few` for 2-4 and for anything ending in 2-4 above twenty,
     * `many` for 5-21 and for the rest. Twenty-two takes `few` and twenty-five
     * takes `many`, which is the pair that shows the rule is the real one
     * rather than a range.
     */
    @Test
    fun polishPicksAmongFourForms() = runBlocking {
        Locale.setDefault(Locale.forLanguageTag("pl"))

        assertEquals("1 strona", getPluralString(Res.plurals.book_length_pages, 1, 1))
        assertEquals("2 strony", getPluralString(Res.plurals.book_length_pages, 2, 2))
        assertEquals("5 stron", getPluralString(Res.plurals.book_length_pages, 5, 5))
        assertEquals("22 strony", getPluralString(Res.plurals.book_length_pages, 22, 22))
        assertEquals("25 stron", getPluralString(Res.plurals.book_length_pages, 25, 25))
    }

    /** Spanish and Italian keep the two forms, as most of Europe does. */
    @Test
    fun spanishAndItalianKeepTwoForms() = runBlocking {
        Locale.setDefault(Locale.forLanguageTag("es"))
        assertEquals("1 página", getPluralString(Res.plurals.book_length_pages, 1, 1))
        assertEquals("5 páginas", getPluralString(Res.plurals.book_length_pages, 5, 5))

        Locale.setDefault(Locale.ITALIAN)
        assertEquals("1 pagina", getPluralString(Res.plurals.book_length_pages, 1, 1))
        assertEquals("5 pagine", getPluralString(Res.plurals.book_length_pages, 5, 5))
    }

    /**
     * Three languages, three answers to the same question about one character.
     * Spanish keeps a hard space before the percent sign; Italian and Polish
     * write none at all.
     */
    @Test
    fun theyDisagreeAboutTheSpaceBeforeThePercentSign() = runBlocking {
        Locale.setDefault(Locale.forLanguageTag("es"))
        assertEquals("125\u00A0%", getString(Res.string.reader_width_percent, 125))

        Locale.setDefault(Locale.ITALIAN)
        assertEquals("125%", getString(Res.string.reader_width_percent, 125))

        Locale.setDefault(Locale.forLanguageTag("pl"))
        assertEquals("125%", getString(Res.string.reader_width_percent, 125))
    }
}
