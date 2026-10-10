package io.lukia.features.translate.infrastructure.wiktionary

import io.lukia.core.domain.AppError
import io.lukia.core.domain.toAppError
import io.lukia.features.translate.domain.settings.TargetLanguage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The whole entry, as opposed to the line of it that fits over a word.
 *
 * The same pages [WiktionaryTranslator] reads and a different amount of them,
 * which is the only thing separating the two - so what is worth holding here is
 * that the entry is not cut to what a paragraph can carry: every sense, every
 * part of speech the word has, and the words the page lists beside them.
 *
 * Which word an entry is followed to is [WiktionaryFormsTest]'s.
 */
class WiktionaryDictionaryTest {

    @Test
    fun everySenseTheEntryHasComesBack() = runTest {
        val entry = wiktionaryDictionary(respondingWith(MANY_SENSES))
            .define("set", TargetLanguage.Finnish)

        assertEquals(
            listOf("asettaa", "laittaa", "sarja", "joukko", "kiinteä"),
            entry.senses,
        )
    }

    /** More than the three a word in a paragraph can carry - see WiktionaryTranslator. */
    @Test
    fun theEntryIsNotCutToWhatFitsOverAWord() = runTest {
        val entry = wiktionaryDictionary(respondingWith(MANY_SENSES))
            .define("set", TargetLanguage.Finnish)

        assertTrue(entry.senses.size > 3, "the entry was cut to ${entry.senses.size} senses")
    }

    @Test
    fun theWordItIsAboutComesBackWithIt() = runTest {
        val entry = wiktionaryDictionary(respondingWith(MANY_SENSES))
            .define("  set  ", TargetLanguage.Finnish)

        assertEquals("set", entry.word)
    }

    /**
     * The bug that made the dictionary look broken on perfectly ordinary words.
     *
     * A Wiktionary title is case sensitive in a way a Wikipedia title is not:
     * `talo` has a page and `Talo` has the proper noun's or none at all. So every
     * word that happened to open a sentence came back as a word no dictionary had
     * ever heard of, while the entry sat there under the same letters in lower
     * case.
     */
    @Test
    fun aWordThatOpenedASentenceIsLookedUpInLowerCaseToo() = runTest {
        val asked = mutableListOf<String>()
        val dictionary = wiktionaryDictionary(
            responding { request ->
                val title = request.url.parameters["titles"].orEmpty()
                asked += title

                if (title == "talo") TALO_ENTRY else MISSING_PAGE
            },
        )

        val entry = dictionary.define("Talo", TargetLanguage.Finnish)

        assertEquals(listOf("asuinrakennus"), entry.senses)
        assertEquals(listOf("Talo", "talo"), asked)
    }

    /**
     * A word is not one word. `mean` is an adjective that is `ilkeä`, a noun that
     * is `keskiarvo` and a verb that is `tarkoittaa`, and which one a sentence
     * meant is settled by the sentence - so an entry that keeps one of them has
     * thrown the answer away two times in three.
     */
    @Test
    fun everyPartOfSpeechTheWordHasComesBack() = runTest {
        val entry = wiktionaryDictionary(respondingWith(MEAN_ENTRY))
            .define("mean", TargetLanguage.Finnish)

        assertEquals(
            listOf("Adjektiivi", "Substantiivi", "Verbi"),
            entry.meanings.map { it.partOfSpeech },
        )
    }

    @Test
    fun eachPartOfSpeechKeepsItsOwnSenses() = runTest {
        val entry = wiktionaryDictionary(respondingWith(MEAN_ENTRY))
            .define("mean", TargetLanguage.Finnish)

        assertEquals(listOf("tarkoittaa, merkitä"), entry.meanings.last().senses)
    }

    /**
     * The next language along is a different word that happens to be spelled the
     * same, and its senses are not this word's.
     */
    @Test
    fun anotherLanguagesPartOfSpeechIsNotOneOfThem() = runTest {
        val entry = wiktionaryDictionary(respondingWith(MEAN_ENTRY))
            .define("mean", TargetLanguage.Finnish)

        assertTrue(entry.senses.none { it.contains("mear") }, entry.senses.toString())
    }

    /**
     * The English Wiktionary opens an entry with how the word sounds, and read as
     * a definition that is what ends up over the word in the book.
     */
    @Test
    fun aPronunciationIsNotWhatTheWordMeans() = runTest {
        val dictionary = wiktionaryDictionary(
            responding { request ->
                if (request.url.parameters["titles"] == "skills") PLURAL_OF else SKILL_ENTRY
            },
        )

        val entry = dictionary.define("skills", TargetLanguage.Finnish)

        assertTrue(entry.senses.none { it.contains("IPA") }, entry.senses.toString())
    }

    /**
     * And the words the entry lists beside its senses, which are what somebody
     * learning the language looks up next.
     */
    @Test
    fun theWordsListedBesideTheEntryComeBack() = runTest {
        val entry = wiktionaryDictionary(respondingWith(SKILL_ENTRY))
            .define("skill", TargetLanguage.Finnish)

        assertEquals(listOf("talent", "skilled", "motor skill"), entry.related)
    }

    /** The next language's section is beside this entry rather than part of it. */
    @Test
    fun anotherLanguagesSectionIsNotListedAsRelated() = runTest {
        val entry = wiktionaryDictionary(respondingWith(SKILL_ENTRY))
            .define("skill", TargetLanguage.Finnish)

        assertTrue(entry.related.none { it == "adresse" }, entry.related.toString())
    }

    @Test
    fun aWordInNoDictionaryAtAllSaysSo() = runTest {
        val failure = runCatching {
            wiktionaryDictionary(respondingWith(MISSING_PAGE))
                .define("set", TargetLanguage.English)
        }.exceptionOrNull()?.toAppError() as? AppError.Unexpected

        assertTrue(
            failure?.explanation.orEmpty().contains("No dictionary entry"),
            failure?.explanation.orEmpty(),
        )
    }
}
