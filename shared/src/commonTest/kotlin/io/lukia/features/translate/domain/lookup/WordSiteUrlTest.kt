package io.lukia.features.translate.domain.lookup

import io.lukia.features.translate.domain.settings.TargetLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Writing one word into somebody else's address.
 *
 * The cases worth holding are the ones that would send the reader somewhere
 * wrong rather than somewhere useless: a word that needs escaping, a language
 * tag a site has never heard of, and a template with nowhere to put the word.
 */
class WordSiteUrlTest {

    @Test
    fun theWordGoesWhereTheTemplateSaysItDoes() {
        val url = WordSite.Custom.urlFor(
            template = "https://example.org/search?q={word}",
            word = "valas",
            targetLanguage = TargetLanguage.Finnish,
        )

        assertEquals("https://example.org/search?q=valas", url)
    }

    /**
     * The site with names of its own for the languages, which is the case that
     * would otherwise open on the wrong pair - or on none.
     *
     * The numbers are sanakirja.org's own, off the two language menus on its
     * search page; `l` is what is being read and `l2` what it is being read
     * into.
     */
    @Test
    fun aSiteWithItsOwnNamesForLanguagesGetsThoseNames() {
        val url = WordSite.Sanakirja.urlFor(
            template = WordSite.Sanakirja.urlTemplate,
            word = "whale",
            targetLanguage = TargetLanguage.Finnish,
            sourceLanguage = "en",
        )

        assertEquals("https://www.sanakirja.org/search.php?q=whale&l=3&l2=17", url)
    }

    /**
     * A language that site has no entry for is asked about across every language
     * it does have, which finds the word rather than refusing to look.
     */
    @Test
    fun aLanguageASiteDoesNotHaveFallsBackToAllOfThem() {
        val url = WordSite.Sanakirja.urlFor(
            template = WordSite.Sanakirja.urlTemplate,
            word = "whale",
            targetLanguage = TargetLanguage.Korean,
            sourceLanguage = "en",
        )

        assertEquals("https://www.sanakirja.org/search.php?q=whale&l=3&l2=-1", url)
    }

    /** And a book that declared no language at all is the same question. */
    @Test
    fun aBookThatDeclaredNoLanguageIsAskedAcrossAllOfThem() {
        val url = WordSite.Sanakirja.urlFor(
            template = WordSite.Sanakirja.urlTemplate,
            word = "whale",
            targetLanguage = TargetLanguage.Finnish,
        )

        assertEquals("https://www.sanakirja.org/search.php?q=whale&l=-1&l2=17", url)
    }

    /** Both languages, for the sites that take a pair. */
    @Test
    fun theLanguagesAreFilledInWhereTheSiteAsksForThem() {
        val url = WordSite.GoogleTranslate.urlFor(
            template = WordSite.GoogleTranslate.urlTemplate,
            word = "whale",
            targetLanguage = TargetLanguage.Finnish,
            sourceLanguage = "en",
        )

        assertEquals(
            "https://translate.google.com/?sl=en&tl=fi&text=whale&op=translate",
            url,
        )
    }

    /** A book that never said what it is written in is not a reason not to go. */
    @Test
    fun aBookThatDeclaredNoLanguageIsSentAsAuto() {
        val url = WordSite.GoogleTranslate.urlFor(
            template = WordSite.GoogleTranslate.urlTemplate,
            word = "whale",
            targetLanguage = TargetLanguage.Finnish,
        )

        assertTrue(url.orEmpty().contains("sl=auto"), url.orEmpty())
    }

    /**
     * There is no `zh-Hans.wiktionary.org`, and an EPUB that declares `pt-BR` is
     * the ordinary case rather than the exception.
     */
    @Test
    fun aTagWithASubtagIsShortenedToTheLanguage() {
        val url = WordSite.Wiktionary.urlFor(
            template = WordSite.Wiktionary.urlTemplate,
            word = "鯨",
            targetLanguage = TargetLanguage.ChineseSimplified,
        )

        assertEquals("https://zh.wiktionary.org/wiki/%E9%AF%A8", url)
    }

    /** A space escaped as `+` is a plus sign once it lands in a path. */
    @Test
    fun aWordThatNeedsEscapingIsPercentEncoded() {
        val url = WordSite.Custom.urlFor(
            template = "https://example.org/{word}",
            word = "à la",
            targetLanguage = TargetLanguage.English,
        )

        assertEquals("https://example.org/%C3%A0%20la", url)
    }

    /**
     * A template with nowhere to put the word would open a browser on a search
     * page with no search in it, which is worse than the button doing nothing.
     */
    @Test
    fun aTemplateThatNeverNamesTheWordGoesNowhere() {
        val url = WordSite.Custom.urlFor(
            template = "https://example.org/",
            word = "whale",
            targetLanguage = TargetLanguage.English,
        )

        assertNull(url)
    }

    @Test
    fun aWordOfNothingButSpacesGoesNowhere() {
        val url = WordSite.Custom.urlFor(
            template = "https://example.org/{word}",
            word = "   ",
            targetLanguage = TargetLanguage.English,
        )

        assertNull(url)
    }
}
