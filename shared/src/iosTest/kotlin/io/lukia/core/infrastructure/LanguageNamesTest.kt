package io.lukia.core.infrastructure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * A language tag as a language, on iOS.
 *
 * The rule is the one the JVM test of the same name pins - a region is a
 * distinction for a spell checker, not a reader - pinned again here because this
 * platform reaches a different table and had drifted:
 * `localizedStringForLanguageCode` wants a language and answers nothing to a tag.
 *
 * Asserted against each other rather than against words, the words depending on
 * the simulator's language and the rule not.
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
