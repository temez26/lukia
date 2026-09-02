package io.lukia.features.reader.presentation

import io.lukia.core.domain.AppError
import io.lukia.core.domain.AppErrorException
import io.lukia.features.reader.presentation.translation.WordOnThePage
import io.lukia.features.reader.presentation.translation.onPassageHold
import io.lukia.features.reader.presentation.translation.onPassageSelected
import io.lukia.features.reader.presentation.translation.translations
import io.lukia.features.reader.presentation.translation.wordInquiry
import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.domain.Translator
import io.lukia.features.translate.domain.text.PassageUnit
import io.lukia.features.translate.domain.text.TextAnchor
import io.lukia.features.translate.presentation.PassageSelection
import io.lukia.features.translate.presentation.PassageTranslation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Which words a hold opens, decided by what has already become of them.
 *
 * Tested here rather than against the reader's own text, because the text raises
 * a hold for any word it can find and only the session knows whether that word
 * has an answer - see [io.lukia.features.reader.presentation.translation.onPassageHold].
 */
class ReaderWordInquiryTest : ReaderViewModelTestCase() {

    /**
     * A word the reader's own service could not answer is exactly the word they
     * want to take somewhere else, so it opens the panel rather than quietly
     * asking the same service again.
     */
    @Test
    fun aWordThatCouldNotBeTranslatedOpensWhatElseItMeans() = runTest {
        val viewModel = readerWith(FailingTranslator)

        viewModel.onPassageSelected(word)
        viewModel.translations.first { it[word.anchor] is PassageTranslation.Failed }

        viewModel.onPassageHold(word)

        val inquiry = assertNotNull(viewModel.wordInquiry.value, "the failure opened nothing")

        assertEquals("Ishmael", inquiry.word)
        assertIs<WordOnThePage.Failed>(inquiry.onThePage)
    }

    @Test
    fun aTranslatedWordOpensWithTheWayBackToTheBooksOwn() = runTest {
        val viewModel = readerWith(AnsweringTranslator)

        viewModel.onPassageSelected(word)
        viewModel.translations.first { it[word.anchor] is PassageTranslation.Ready }

        viewModel.onPassageHold(word)

        assertEquals(
            WordOnThePage.Translated,
            assertNotNull(viewModel.wordInquiry.value).onThePage,
        )
    }

    /** A word still arriving has nothing more to say yet, and it is about to. */
    @Test
    fun aWordStillArrivingIsLeftToArrive() = runTest {
        val viewModel = readerWith(NeverAnsweringTranslator)

        viewModel.onPassageSelected(word)
        viewModel.translations.first { it[word.anchor] is PassageTranslation.Loading }

        viewModel.onPassageHold(word)

        assertNull(viewModel.wordInquiry.value, "a word still arriving opened a panel")
    }

    /** And one nobody has asked about at all is asked about, as the gesture says. */
    @Test
    fun aWordNobodyHasAskedAboutIsAskedAbout() = runTest {
        val viewModel = readerWith(AnsweringTranslator)

        viewModel.onPassageHold(word)
        viewModel.translations.first { it[word.anchor] is PassageTranslation.Ready }

        assertNull(viewModel.wordInquiry.value, "an untouched word opened a panel")
    }

    private suspend fun readerWith(translator: Translator): ReaderViewModel {
        val viewModel = createViewModel(
            document = pdfInLibrary(pageCount = 1, text = "Call me Ishmael."),
            translations = testTranslations(translator),
        )

        viewModel.settled()

        return viewModel
    }

    private companion object {

        /** One word of the open book, addressed the way the page would address it. */
        val word = PassageSelection(
            anchor = TextAnchor.Page(pageIndex = 0, sentenceIndex = 0, wordIndex = 2),
            passage = "Ishmael",
            surroundingText = "Call me Ishmael.",
            unit = PassageUnit.Word,
        )

        val AnsweringTranslator = Translator { request, _ -> Translation("Ismael") }

        val FailingTranslator = Translator { _, _ ->
            throw AppErrorException(AppError.Unexpected("The service did not answer."))
        }

        /** Still in flight for as long as the test cares to look at it. */
        val NeverAnsweringTranslator = Translator { _, _ ->
            CompletableDeferred<Translation>().await()
        }
    }
}
