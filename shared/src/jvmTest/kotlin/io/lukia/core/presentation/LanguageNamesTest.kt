package io.lukia.core.presentation

import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A language tag as a language.
 *
 * In `jvmTest` rather than beside the rest of the metadata tests because the
 * answer now depends on the device's own locale, and a test asserting an English
 * name has to be the one that says so.
 */
class LanguageNamesTest {

    private val wasDefault: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(wasDefault)

    @Test
    fun readsALanguageTagAsALanguage() {
        Locale.setDefault(Locale.ENGLISH)

        assertEquals("English", languageName("en"))
        assertEquals("Finnish", languageName("fi"))
    }

    /** The region is a distinction for a spell checker, not for a reader. */
    @Test
    fun ignoresTheRegionSubtag() {
        Locale.setDefault(Locale.ENGLISH)

        assertEquals("English", languageName("en-GB"))
        assertEquals("Finnish", languageName("fi_FI"))
    }

    /**
     * The reason the table went. Aymara was one of the languages it did not
     * list, and a book in it used to show as "AY".
     */
    @Test
    fun namesALanguageTheOldTableDidNotHold() {
        Locale.setDefault(Locale.ENGLISH)

        assertEquals("Aymara", languageName("ay"))
    }

    /** And the reason it had to be the device's table rather than one of ours. */
    @Test
    fun namesALanguageInTheLanguageTheDeviceIsSetTo() {
        Locale.setDefault(Locale.GERMAN)

        assertEquals("Finnisch", languageName("fi"))
    }

    /** A tag nothing recognises is still more use to show than to hide. */
    @Test
    fun anUnknownTagIsShownAsItself() {
        Locale.setDefault(Locale.ENGLISH)

        assertEquals("ZZ", languageName("zz"))
    }

    @Test
    fun noLanguageIsNoName() {
        assertNull(languageName(null))
        assertNull(languageName("  "))
    }
}
