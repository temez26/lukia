package io.lukia.features.translate.infrastructure

import io.lukia.features.translate.domain.TargetLanguage
import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.domain.TranslationRequest
import io.lukia.features.translate.domain.translate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CoalescingTranslatorTest {

    private val passage = "Call me Ishmael."

    /**
     * The same line pointed at twice while the first answer is still coming
     * back - a repeated line of dialogue, a running header - is one question.
     */
    @Test
    fun twoAsksForTheSameWordsAtOnceAreOneRequest() = runTest {
        val answer = CompletableDeferred<Translation>()
        var asked = 0

        val translator = CoalescingTranslator { _, _ ->
            asked++
            answer.await()
        }

        val first = async { translator.translate(request()) }
        val second = async { translator.translate(request()) }
        runCurrent()

        assertEquals(1, asked)

        answer.complete(Translation("Kutsukaa minua Ismaeliksi."))

        assertEquals("Kutsukaa minua Ismaeliksi.", first.await().text)
        assertEquals("Kutsukaa minua Ismaeliksi.", second.await().text)
    }

    @Test
    fun differentWordsAreDifferentRequests() = runTest {
        val asked = mutableListOf<String>()

        val translator = CoalescingTranslator { request, _ ->
            asked += request.text
            Translation("Translated ${request.text}")
        }

        translator.translate(request())
        translator.translate(request(text = "Some years ago."))

        assertEquals(listOf(passage, "Some years ago."), asked)
    }

    /**
     * Only concurrent asks. Once an answer is in, remembering it is
     * [CachingTranslator]'s job - and this one holding on to it would be a
     * second cache nobody could clear.
     */
    @Test
    fun askingAgainAfterAnAnswerIsInAsksAgain() = runTest {
        var asked = 0

        val translator = CoalescingTranslator { _, _ ->
            asked++
            Translation("Kutsukaa minua Ismaeliksi.")
        }

        translator.translate(request())
        translator.translate(request())

        assertEquals(2, asked)
    }

    /** Everybody waiting on an answer that did not come gets told so. */
    @Test
    fun aFailureReachesEveryoneWaitingOnIt() = runTest {
        val answer = CompletableDeferred<Translation>()

        val translator = CoalescingTranslator { _, _ -> answer.await() }

        // Caught inside each waiter rather than at the join: a failing `async`
        // takes its parent down with it, which would end the test rather than
        // let it ask what the second waiter saw.
        val first = async { runCatching { translator.translate(request()) } }
        val second = async { runCatching { translator.translate(request()) } }
        runCurrent()

        answer.completeExceptionally(RuntimeException("no network"))

        assertEquals("no network", first.await().exceptionOrNull()?.message)
        assertEquals("no network", second.await().exceptionOrNull()?.message)
    }

    /** A request that failed is not one that can never be asked about again. */
    @Test
    fun aFailedRequestCanBeAskedAgain() = runTest {
        var attempt = 0

        val translator = CoalescingTranslator { _, _ ->
            attempt++
            if (attempt == 1) throw RuntimeException("no network") else Translation("Second time.")
        }

        assertFailsWith<RuntimeException> { translator.translate(request()) }

        assertEquals("Second time.", translator.translate(request()).text)
    }

    private fun request(text: String = passage) =
        TranslationRequest(text = text, targetLanguage = TargetLanguage.Finnish)
}
