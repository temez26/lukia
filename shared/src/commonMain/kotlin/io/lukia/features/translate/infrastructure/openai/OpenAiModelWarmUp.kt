package io.lukia.features.translate.infrastructure.openai

import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.lukia.core.domain.AppError
import io.lukia.core.domain.AppErrorException
import io.lukia.core.domain.diagnostics.Diagnostics
import io.lukia.features.translate.domain.provider.ModelWarmUp
import io.lukia.features.translate.infrastructure.TranslationSettingsRepository
import io.lukia.features.translate.infrastructure.refuseCleartextToThePublicInternet
import io.lukia.features.translate.infrastructure.translating
import io.lukia.features.translate.infrastructure.translationTimeouts
import kotlinx.serialization.json.Json

/**
 * The shortest thing that still counts as work.
 *
 * A server loads a model when it is asked to run one, and not before - there is
 * no "please load this" in the API every local server implements. So this asks
 * for a single token about nothing, and throws the token away: what is being
 * waited for is the loading, not the answer.
 */
private const val WARM_UP_PROMPT = "Hi"

/**
 * Has a locally hosted model loaded before anybody needs it.
 *
 * The wait this removes is only the first one: a large model is read off disk
 * the first time it is asked to run, which can take minutes, and every request
 * after is fast. Paid here it is paid on the settings screen, where a reader has
 * just chosen a model and expects something to happen - rather than on the first
 * sentence they tap three chapters into a book.
 */
class OpenAiModelWarmUp(
    private val client: HttpClient,
    private val settings: TranslationSettingsRepository,
    private val diagnostics: Diagnostics = Diagnostics.None,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ModelWarmUp {

    override suspend fun warmUp() {
        val configured = settings.settings.value

        configured.refuseCleartextToThePublicInternet()

        val response = translating(configured, diagnostics) {
            client.post("${configured.baseUrl.trimEnd('/')}/chat/completions") {
                translationTimeouts(configured)

                configured.apiKey?.takeIf { it.isNotBlank() }?.let { key ->
                    headers { append("Authorization", "Bearer $key") }
                }
                contentType(ContentType.Application.Json)
                setBody(
                    ChatRequest(
                        model = configured.model,
                        maxTokens = 1,
                        messages = listOf(ChatMessage.user(WARM_UP_PROMPT)),
                    ),
                )
            }
        }

        if (response.status.isSuccess()) return

        // Nearly always a name that does not match anything the server has, and
        // the server says which far better than a guess from the status would.
        val reported = runCatching {
            json.decodeFromString<ChatError>(response.bodyAsText()).error?.message
        }.getOrNull()

        throw AppErrorException(
            AppError.Unexpected(
                reported ?: "${configured.model} could not be loaded. Check it is one of " +
                    "the models this server offers.",
            ),
        )
    }
}
