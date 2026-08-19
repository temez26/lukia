package io.lukia.features.translate.infrastructure.openai

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.isSuccess
import io.lukia.core.domain.AppError
import io.lukia.core.domain.AppErrorException
import io.lukia.features.translate.domain.ModelCatalogue
import io.lukia.features.translate.infrastructure.TranslationSettingsRepository
import io.lukia.features.translate.infrastructure.asTranslationFailure
import io.lukia.features.translate.infrastructure.translationTimeouts
import kotlinx.coroutines.CancellationException

/**
 * Asks an OpenAI-compatible server what it can run.
 *
 * The reason this exists at all is the local case: a hosted service publishes
 * its catalogue and it barely changes, but a model on somebody's own machine is
 * whichever file they loaded, under a name that has to match exactly. Typing
 * `qwen2.5-7b-instruct` from memory and getting one character wrong is a 400
 * that reads like the whole feature is broken.
 */
class OpenAiModelCatalogue(
    private val client: HttpClient,
    private val settings: TranslationSettingsRepository
) : ModelCatalogue {

    override suspend fun models(): List<String> {
        val configured = settings.settings.value

        if (!configured.isEndpointAllowed) {
            throw AppErrorException(
                AppError.Unexpected(
                    "Translating over plain HTTP is only allowed to your own network."
                )
            )
        }

        val response = try {
            client.get("${configured.baseUrl.trimEnd('/')}/models") {
                translationTimeouts(configured)

                configured.apiKey?.takeIf { it.isNotBlank() }?.let { key ->
                    headers { append("Authorization", "Bearer $key") }
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            throw failure.asTranslationFailure(configured)
        }

        if (!response.status.isSuccess()) {
            throw AppErrorException(
                AppError.Unexpected(
                    "${configured.baseUrl} did not answer with a list of models. Check the " +
                        "address - it should end in /v1."
                )
            )
        }

        val models = response.body<ModelCatalogueResponse>().data
            .map { it.id }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        // A server answering with an empty catalogue has nothing loaded, which
        // is a state worth naming rather than showing as an empty list that
        // looks like the request never happened.
        if (models.isEmpty()) {
            throw AppErrorException(
                AppError.Unexpected("That server has no models loaded at the moment.")
            )
        }

        return models
    }
}
