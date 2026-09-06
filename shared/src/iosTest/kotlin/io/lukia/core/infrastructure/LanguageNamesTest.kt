package io.lukia.core.infrastructure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * A language tag as a language, on iOS.
 *
 * The rule being pinned is the one the JVM test of the same name pins - the
 * region is a distinction for a spell checker, not for a reader - and it is
 * pinned separately here because this platform reaches a different table and had
 * drifted from it: `localizedStringForLanguageCode` is given a language, and
 * handed a whole tag it answers nothing.
 *
 * Asserted against each other rather than against words, because the words
 * depend on the language the simulator is set to and the rule does not.
 */
class LanguageNamesTest {

    @Test
    fun readsALanguageTagAsALanguage() {
        assertNotNull(languageDisplayName("fi"))
    }

    @Test
    fun ignoresTheRegionSubtag() {
        assertEquals(languageDisplayName("pt"), languageDisplayName("pt-BR"))
        assertEquals(languageDisplayName("fi"), languageDisplayName("fi_FI"))
    }

    /** A script is a distinction for a typesetter, and is dropped the same way. */
    @Test
    fun ignoresTheScriptSubtag() {
        assertEquals(languageDisplayName("zh"), languageDisplayName("zh-Hant"))
    }

    @Test
    fun noLanguageIsNoName() {
        assertNull(languageDisplayName(""))
        assertNull(languageDisplayName("-FI"))
    }
}
