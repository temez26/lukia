package io.lukia.features.translate.infrastructure

import io.lukia.core.domain.text.PassageUnit
import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.domain.TranslationRequest
import io.lukia.features.translate.domain.Translator
import io.lukia.features.translate.domain.settings.TargetLanguage
import io.lukia.features.translate.domain.settings.WordLookup
import io.lukia.features.translate.domain.translate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Where a passage goes, which is a question about how much of it there is.
 *
 * The split is at the word because that is where the book stops mattering: which
 * sense of a word a sentence means is settled by the paragraph around it, and a
 * service handed neither cannot do better than guess.
 */
class WordLookupTranslatorTest {

    @Test
    fun aWordGoesWhereTheReaderSaidWordsShouldGo() = runTest {
        val asked = mutableListOf<String>()
        val translator = lookingUpWith(WordLookup.Google, asked)

        translator.translate(request("whale", PassageUnit.Word))

        assertEquals(listOf("google"), asked)
    }

    @Test
    fun aDictionaryAnswersWhenOneIsChosen() = runTest {
        val asked = mutableListOf<String>()
        val translator = lookingUpWith(WordLookup.Wiktionary, asked)

        translator.translate(request("whale", PassageUnit.Word))

        assertEquals(listOf("dictionary"), asked)
    }

    @Test
    fun aWordStillGoesToTheTranslatorWhenThatIsWhatIsChosen() = runTest {
        val asked = mutableListOf<String>()
        val translator = lookingUpWith(WordLookup.Translator, asked)

        translator.translate(request("whale", PassageUnit.Word))

        assertEquals(listOf("translator"), asked)
    }

    /**
     * The point of the whole thing: a sentence carries the book with it, and
     * nothing that ignores the book is offered it however words are set.
     */
    @Test
    fun aSentenceGoesToTheTranslatorWhateverWordsAreSetTo() = runTest {
        WordLookup.entries.forEach { lookup ->
            val asked = mutableListOf<String>()
            val translator = lookingUpWith(lookup, asked)

            translator.translate(request("Call me Ishmael.", PassageUnit.Sentence))

            assertEquals(listOf("translator"), asked, "words set to $lookup")
        }
    }

    @Test
    fun aParagraphGoesToTheTranslatorToo() = runTest {
        val asked = mutableListOf<String>()
        val translator = lookingUpWith(WordLookup.Wiktionary, asked)

        translator.translate(request("Call me Ishmael. Some years ago.", PassageUnit.Paragraph))

        assertEquals(listOf("translator"), asked)
    }

    private fun lookingUpWith(lookup: WordLookup, asked: MutableList<String>) =
        WordLookupTranslator(
            lookup = { lookup },
            translator = naming("translator", asked),
            google = naming("google", asked),
            dictionary = naming("dictionary", asked),
        )

    private fun naming(name: String, asked: MutableList<String>) = Translator { _, _ ->
        asked += name
        Translation(name)
    }

    private fun request(text: String, unit: PassageUnit) = TranslationRequest(
        text = text,
        targetLanguage = TargetLanguage.Finnish,
        unit = unit,
    )
}
