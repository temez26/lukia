package io.lukia.features.translate.infrastructure.wiktionary

import io.lukia.features.translate.domain.settings.TargetLanguage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Following a word to the one it is a form of.
 *
 * The case that made the dictionary useless on real prose. A book is written in
 * inflected words, and a wiki's page for one of them is usually not a definition
 * at all - `values` answers `monikkomuoto sanasta value`, which tells somebody
 * who wanted `arvo` only that they have more looking up to do.
 *
 * What is worth holding here is both halves of that: which lines are pointers,
 * and which are definitions that merely look like one. Following the wrong word
 * is worse than not following.
 */
class WiktionaryFormsTest {

    @Test
    fun aPageThatOnlySaysWhichWordThisIsAFormOfFollowsToThatWord() = runTest {
        val asked = mutableListOf<String>()
        val dictionary = wiktionaryDictionary(
            responding { request ->
                asked += request.url.parameters["titles"].orEmpty()

                if (request.url.parameters["titles"] == "values") FORM_OF else VALUE_ENTRY
            },
        )

        val entry = dictionary.define("values", TargetLanguage.Finnish)

        assertEquals(listOf("arvo", "hinta"), entry.senses)
        assertEquals(listOf("values", "value"), asked)
    }

    /** And it says which word it answered under, since that is not the one asked. */
    @Test
    fun theWordItAnsweredUnderComesBackWithIt() = runTest {
        val dictionary = wiktionaryDictionary(
            responding { request ->
                if (request.url.parameters["titles"] == "values") FORM_OF else VALUE_ENTRY
            },
        )

        assertEquals("value", dictionary.define("values", TargetLanguage.Finnish).word)
    }

    /** And what the held word is, which is the half the page in the book cannot carry. */
    @Test
    fun whatFormTheHeldWordIsComesBackWithIt() = runTest {
        val dictionary = wiktionaryDictionary(
            responding { request ->
                if (request.url.parameters["titles"] == "values") FORM_OF else VALUE_ENTRY
            },
        )

        val entry = dictionary.define("values", TargetLanguage.Finnish)

        assertEquals("monikkomuoto sanasta value", entry.form)
    }

    /**
     * Followed once and no further, and only when there is something at the other
     * end. A base form with no entry of its own leaves an entry that says what
     * form the word is and nothing about what it means - which is an answer, and
     * belongs in the panel rather than over the word in the book.
     */
    @Test
    fun aBaseFormWithNoEntryLeavesTheFormAndNoMeaning() = runTest {
        val dictionary = wiktionaryDictionary(
            responding { request ->
                if (request.url.parameters["titles"] == "values") FORM_OF else MISSING_PAGE
            },
        )

        val entry = dictionary.define("values", TargetLanguage.Finnish)

        assertEquals("values", entry.word)
        assertEquals("monikkomuoto sanasta value", entry.form)
        assertTrue(entry.hasNoMeaning, entry.senses.toString())
    }

    /**
     * The case a rule about spelling could never reach. `juoksi` and `juosta`
     * share four letters, `went` and `go` share none, and what says they belong
     * together is the wiki's own phrasing rather than anything about the words -
     * which on a language that inflects everything is most of the book.
     */
    @Test
    fun aFormSpelledNothingLikeItsBaseIsStillFollowed() = runTest {
        val dictionary = wiktionaryDictionary(
            responding { request ->
                if (request.url.parameters["titles"] == "juoksi") INFLECTION else RUN_ENTRY
            },
        )

        val entry = dictionary.define("juoksi", TargetLanguage.Finnish)

        assertEquals("juosta", entry.word)
        assertEquals(listOf("liikkua nopeasti jaloin"), entry.senses)
    }

    /** English marks a form with the same word it builds every other phrase with. */
    @Test
    fun anEnglishFormIsFollowedByWhatTheLineIsSaying() = runTest {
        val dictionary = wiktionaryDictionary(
            responding { request ->
                if (request.url.parameters["titles"] == "skills") PLURAL_OF else SKILL_ENTRY
            },
        )

        val entry = dictionary.define("skills", TargetLanguage.Finnish)

        assertEquals(listOf("taito, kyky, kyvykkyys"), entry.meanings.first().senses)
    }

    /**
     * And `of` on its own is not a pointer, or every second definition would be
     * one. A reader looking up a word defined as `a piece of land` wants what it
     * means, not the entry for `land`.
     */
    @Test
    fun aDefinitionThatMerelyContainsOfIsNotFollowed() = runTest {
        val asked = mutableListOf<String>()
        val dictionary = wiktionaryDictionary(
            responding { request ->
                asked += request.url.parameters["titles"].orEmpty()
                PLOT_ENTRY
            },
        )

        assertEquals("plot", dictionary.define("plot", TargetLanguage.Finnish).word)
        assertEquals(listOf("plot"), asked)
    }

    /**
     * A definition is not a pointer. Following the last word of every two-word
     * sense would send a reader from `value` to `hinta`.
     */
    @Test
    fun anEntryThatDefinesTheWordIsNotFollowedAnywhere() = runTest {
        val asked = mutableListOf<String>()
        val dictionary = wiktionaryDictionary(
            responding { request ->
                asked += request.url.parameters["titles"].orEmpty()
                VALUE_ENTRY
            },
        )

        assertEquals("value", dictionary.define("value", TargetLanguage.Finnish).word)
        assertEquals(listOf("value"), asked)
    }
}
