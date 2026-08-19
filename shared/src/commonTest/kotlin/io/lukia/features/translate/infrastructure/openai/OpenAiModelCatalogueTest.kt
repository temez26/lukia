package io.lukia.features.translate.infrastructure.openai

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.lukia.core.domain.AppError
import io.lukia.core.domain.FakeSecretStore
import io.lukia.core.domain.FakeSettingsStore
import io.lukia.core.domain.toAppError
import io.lukia.features.translate.domain.TranslationProvider
import io.lukia.features.translate.domain.TranslationSettings
import io.lukia.features.translate.infrastructure.TranslationSettingsRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Asking a local server what it has loaded.
 *
 * The answers here are the ones LM Studio actually gives, because the point of
 * this is that a reader should never have to type `qwen2.5-7b-instruct` from
 * memory and get one character wrong.
 */
class OpenAiModelCatalogueTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun theServersModelsComeBackNamed() = runTest {
        val catalogue = catalogue(
            answers {
                respondJson(
                    """
                    {"object": "list", "data": [
                      {"id": "qwen2.5-7b-instruct", "object": "model"},
                      {"id": "llama-3.2-3b-instruct", "object": "model"}
                    ]}
                    """,
                )
            },
        )

        assertEquals(
            listOf("llama-3.2-3b-instruct", "qwen2.5-7b-instruct"),
            catalogue.models(),
        )
    }

    @Test
    fun theCatalogueIsAskedForUnderTheConfiguredAddress() = runTest {
        var sent: HttpRequestData? = null
        val catalogue = catalogue(
            answers {
                sent = it
                oneModel()
            },
        )

        catalogue.models()

        assertEquals("http://localhost:1234/v1/models", sent?.url.toString())
    }

    @Test
    fun aTrailingSlashOnTheAddressIsNotADoubledOne() = runTest {
        var sent: HttpRequestData? = null
        val catalogue = catalogue(
            answers {
                sent = it
                oneModel()
            },
            baseUrl = "http://localhost:1234/v1/",
        )

        catalogue.models()

        assertEquals("http://localhost:1234/v1/models", sent?.url.toString())
    }

    /** A server behind a proxy may want a key, and one is sent when given. */
    @Test
    fun aKeyIsSentWhenThereIsOne() = runTest {
        var sent: HttpRequestData? = null
        val catalogue = catalogue(
            answers {
                sent = it
                oneModel()
            },
            apiKey = "behind-a-proxy",
        )

        catalogue.models()

        assertEquals("Bearer behind-a-proxy", sent?.headers?.get("Authorization"))
    }

    /** The commonest state of a local server is not running at all. */
    @Test
    fun aServerThatIsNotThereSaysWhereItWasLookedFor() = runTest {
        val catalogue = catalogue(
            MockEngine { throw kotlinx.io.IOException("Connection refused") },
        )

        val error = catalogue.failure() as AppError.Unexpected

        assertTrue(error.cause.orEmpty().contains("http://localhost:1234/v1"), error.cause.orEmpty())
    }

    /** Pointing at the wrong path is the next commonest. */
    @Test
    fun anAddressThatAnswersWithSomethingElseSaysSo() = runTest {
        val catalogue = catalogue(answers { respondJson("{}", HttpStatusCode.NotFound) })

        val error = catalogue.failure() as AppError.Unexpected

        assertTrue(error.cause.orEmpty().contains("/v1"), error.cause.orEmpty())
    }

    /**
     * A server with nothing loaded answers perfectly well with an empty list.
     * Shown as an empty list it would look like the request never happened.
     */
    @Test
    fun aServerWithNothingLoadedSaysThatRatherThanNothing() = runTest {
        val catalogue = catalogue(answers { respondJson("""{"object": "list", "data": []}""") })

        val error = catalogue.failure() as AppError.Unexpected

        assertTrue(error.cause.orEmpty().contains("no models loaded"), error.cause.orEmpty())
    }

    /** Nothing is asked of a public address over plain HTTP, catalogue included. */
    @Test
    fun cleartextToThePublicInternetIsRefusedBeforeAnythingIsSent() = runTest {
        var asked = false
        val catalogue = catalogue(
            answers {
                asked = true
                oneModel()
            },
            baseUrl = "http://models.example.com/v1",
        )

        catalogue.failure()

        assertTrue(!asked, "a request went out in the clear")
    }

    private fun MockRequestHandleScope.oneModel(): HttpResponseData =
        respondJson("""{"object": "list", "data": [{"id": "local-model"}]}""")

    private fun catalogue(
        engine: MockEngine,
        baseUrl: String = "http://localhost:1234/v1",
        apiKey: String? = null,
    ): OpenAiModelCatalogue {
        val settings = TranslationSettingsRepository(FakeSettingsStore(), FakeSecretStore()).apply {
            update(
                TranslationSettings(provider = TranslationProvider.Local)
                    .withBaseUrl(baseUrl)
                    .withApiKey(apiKey.orEmpty()),
            )
        }

        val client = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(json) }
        }

        return OpenAiModelCatalogue(client, settings)
    }

    private suspend fun OpenAiModelCatalogue.failure(): AppError =
        runCatching { models() }.exceptionOrNull()?.toAppError() ?: error("expected a failure")
}

private fun answers(
    handle: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) = MockEngine { request -> handle(request) }

private fun MockRequestHandleScope.respondJson(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
) = respond(
    content = body.trimIndent(),
    status = status,
    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
)
