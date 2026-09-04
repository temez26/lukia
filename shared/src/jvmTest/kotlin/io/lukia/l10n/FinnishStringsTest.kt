package io.lukia.l10n

import io.lukia.core.presentation.formatFileSize
import io.lukia.features.stats.presentation.streakLine
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
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Finnish, as the four things the English file cannot show.
 *
 * `TranslationsTest` holds the shape of a translation - every key present, no
 * key invented, no argument asked for that the app does not supply. This one
 * holds the four places where Finnish is written differently from English, which
 * is the whole reason the strings left the code.
 */
class FinnishStringsTest {

    private val wasDefault: Locale = Locale.getDefault()

    @BeforeTest
    fun useFinnish() = Locale.setDefault(Locale.forLanguageTag("fi"))

    @AfterTest
    fun restoreLocale() = Locale.setDefault(wasDefault)

    @Test
    fun theAppSpeaksFinnishWhenTheDeviceDoes() {
        assertEquals("Kirjasto", runBlocking { getString(Res.string.nav_library) })
    }

    /**
     * A numeral takes the partitive in Finnish: one page is `sivu` and any other
     * number of them is `sivua`. English picks between two forms on a boolean
     * and would have got this right by luck; the point is that the form comes
     * from the plural resource rather than from an `if` in the code.
     */
    @Test
    fun aCountTakesThePartitive() = runBlocking {
        assertEquals("1 sivu", getPluralString(Res.plurals.book_length_pages, 1, 1))
        assertEquals("348 sivua", getPluralString(Res.plurals.book_length_pages, 348, 348))
    }

    /** And a run of days, which is a sentence rather than a label with a count. */
    @Test
    fun aRunOfDaysReadsAsASentence() {
        assertEquals("Ei putkea menossa", composed { streakLine(0) })
        assertEquals("1 päivä putkeen", composed { streakLine(1) })
        assertEquals("4 päivää putkeen", composed { streakLine(4) })
    }

    /**
     * Finnish puts a space before the percent sign, and a non-breaking one so
     * the figure cannot be left at the end of a line without its unit. This is
     * the case that could not have been fixed in the code at all: the sign was
     * written hard against the number by the source, and only a resource can
     * move it.
     */
    @Test
    fun thePercentSignTakesANonBreakingSpaceBeforeIt() {
        assertEquals(
            "125\u00A0%",
            runBlocking { getString(Res.string.reader_width_percent, 125) },
        )
    }

    /**
     * Two changes at once: the decimal mark is a comma, which the platform's
     * formatter supplies, and the unit is a Finnish abbreviation, which the
     * resource does.
     */
    @Test
    fun aFileSizeIsWrittenTheFinnishWay() {
        assertEquals("1,5\u00A0kt", composed { formatFileSize(1536L) })
    }
}
