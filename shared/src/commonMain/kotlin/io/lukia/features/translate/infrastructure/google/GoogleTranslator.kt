package io.lukia.features.translate.infrastructure.google

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.lukia.core.domain.AppError
import io.lukia.core.domain.AppErrorException
import io.lukia.core.domain.diagnostics.Diagnostics
import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.domain.TranslationRequest
import io.lukia.features.translate.domain.Translator
import io.lukia.features.translate.infrastructure.TranslationSettingsRepository
import io.lukia.features.translate.infrastructure.translating
import io.lukia.features.translate.infrastructure.translationTimeouts
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray

/** Google's supported API, which wants a key and bills for what it translates. */
private const val CLOUD_ENDPOINT = "https://translation.googleapis.com/language/translate/v2"

/**
 * The endpoint the web page and the browser extensions use.
 *
 * Not a published API. It takes no key and costs nothing, which is why it is
 * here at all, and Google is under no obligation to keep it working, answering
 * in this shape, or answering this device at all - it limits by address, and a
 * reader looking up a great many words may find it stops. Using it is also
 * outside Google's terms.
 *
 * So it is the fallback rather than the default: the moment a reader puts a key
 * in Settings, every request goes to the supported one instead. Anybody relying
 * on this for real reading should add a key.
 */
private const val PUBLIC_ENDPOINT = "https://translate.googleapis.com/translate_a/single"

/**
 * Translates through Google, which knows nothing about the book.
 *
 * Offered for single words only - see
 * [io.lukia.features.reader.domain.text.WordLookup]. A word is the one thing a
 * translator with no context can be relied on for, and the one thing a reader
 * may reasonably want answered in a tenth of a second rather than in two
 * seconds by a model.
 *
 * Which of the two endpoints is used comes from whether the reader has given a
 * key. Both answer the same question and neither is asked anything about the
 * book, so nothing above this line changes with the choice.
 */
class GoogleTranslator(
    private val client: HttpClient,
    private val settings: TranslationSettingsRepository,
    private val diagnostics: Diagnostics = Diagnostics.None,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : Translator {

    override suspend fun translate(
        request: TranslationRequest,
        onPartial: (String) -> Unit,
    ): Translation {
        val configured = settings.settings.value
        val key = configured.googleApiKey

        val text = translating(configured, diagnostics) {
            if (key == null) askPublicly(request) else askCloud(request, key)
        }

        if (text.isBlank()) throw AppErrorException(AppError.TranslationFailed)

        // No note: Google gives one rendering and says nothing about why. A note
        // invented here would be this app putting words in its mouth.
        return Translation(text = text)
    }

    private suspend fun askCloud(request: TranslationRequest, key: String): String {
        val configured = settings.settings.value

        val response = client.post(CLOUD_ENDPOINT) {
            translationTimeouts(configured)
            // A header rather than `?key=`, which the endpoint also accepts.
            // A query string is the one part of a request that gets written
            // down by everything it passes - proxies, server logs, a crash
            // report's copy of the URL - and a key that has been written down
            // somewhere nobody is watching is a key that has leaked.
            headers { append("X-Goog-Api-Key", key) }
            contentType(ContentType.Application.Json)
            setBody(
                CloudRequest(
                    q = listOf(request.text),
                    target = request.targetLanguage.tag,
                    format = "text",
                ),
            )
        }

        if (!response.status.isSuccess()) {
            throw AppErrorException(
                AppError.Unexpected(
                    response.googleError()
                        ?: "Google would not translate that. Check the Cloud Translation " +
                        "key in Settings.",
                ),
            )
        }

        return runCatching {
            json.decodeFromString<CloudResponse>(response.bodyAsText())
                .data
                ?.translations
                ?.firstOrNull()
                ?.translatedText
        }.getOrNull().orEmpty()
    }

    /**
     * The public endpoint's answer, which is nested arrays rather than an object
     * with names.
     *
     * It comes back as `[[["translated","original",...],...],...]` - one entry
     * per piece it decided to split the text into, each with the translation
     * first. A word is one piece, but the pieces are joined anyway rather than
     * assuming so: it costs a line and it is the difference between a correct
     * answer and half of one the first time somebody looks up a hyphenated word.
     */
    private suspend fun askPublicly(request: TranslationRequest): String {
        val response = client.get(PUBLIC_ENDPOINT) {
            translationTimeouts(settings.settings.value)
            parameter("client", "gtx")
            parameter("sl", "auto")
            parameter("tl", request.targetLanguage.tag)
            parameter("dt", "t")
            parameter("q", request.text)
        }

        if (!response.status.isSuccess()) {
            throw AppErrorException(
                AppError.Unexpected(
                    "Google would not answer. It limits how often it will be asked " +
                        "without a key - add a Cloud Translation key in Settings, or " +
                        "look words up with the translator instead.",
                ),
            )
        }

        val pieces = runCatching {
            json.parseToJsonElement(response.bodyAsText()).jsonArray.firstOrNull()?.jsonArray
        }.getOrNull() ?: return ""

        return pieces.joinToString("") { piece ->
            (piece as? JsonArray)?.firstOrNull()?.let { it as? JsonPrimitive }?.content.orEmpty()
        }
    }

    /** Google's own account of what went wrong, when it sent one. */
    private suspend fun io.ktor.client.statement.HttpResponse.googleError(): String? = runCatching {
        json.decodeFromString<CloudError>(bodyAsText()).error?.message
    }.getOrNull()
}

@Serializable
private data class CloudRequest(
    val q: List<String>,
    val target: String,
    val format: String,
)

@Serializable
private data class CloudResponse(val data: CloudTranslations? = null)

@Serializable
private data class CloudTranslations(val translations: List<CloudTranslation> = emptyList())

@Serializable
private data class CloudTranslation(
    @SerialName("translatedText") val translatedText: String = "",
)

@Serializable
private data class CloudError(val error: CloudErrorBody? = null)

@Serializable
private data class CloudErrorBody(val message: String? = null)
