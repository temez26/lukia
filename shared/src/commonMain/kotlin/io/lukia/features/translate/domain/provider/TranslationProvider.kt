package io.lukia.features.translate.domain.provider

/**
 * Who does the translating.
 *
 * Three entries rather than two implementations: [OpenAi] and [Local] speak the
 * same wire format, because everything that runs a model locally - LM Studio,
 * Ollama, llama.cpp's server, vLLM - exposes OpenAI's chat endpoint. They are
 * separate here only because they are configured differently and fail
 * differently, and telling somebody "your key was rejected" when they have no
 * key is not help.
 */
enum class TranslationProvider(
    val label: String,
    /** Where requests go unless the reader says otherwise. */
    val defaultBaseUrl: String,
    /** What is asked for when nothing has been chosen. */
    val defaultModel: String,
    /** Whether this is unusable without a key, as opposed to merely able to take one. */
    val requiresKey: Boolean,
    /** Whether the reader is expected to point this somewhere themselves. */
    val hasConfigurableEndpoint: Boolean,
    /**
     * How long this is allowed to say nothing before the wait is given up on.
     *
     * Silence rather than total duration, because the two providers fail on
     * opposite scales: a hosted service that has not answered in a minute is
     * not going to, while a local server given a model it has not loaded yet
     * spends minutes reading a file off disk before it can say a word. One
     * number for both is either a hosted request that hangs or a local one that
     * is abandoned while it is still working.
     */
    val defaultTimeoutSeconds: Int,
    /**
     * Models worth offering as a starting point. Deliberately not the only
     * choices: what a service serves changes faster than an app ships, and a
     * local server's models are whatever the reader happens to have loaded.
     */
    val suggestedModels: List<String>,
) {
    Anthropic(
        label = "Anthropic",
        defaultBaseUrl = "https://api.anthropic.com/v1",
        defaultModel = "claude-opus-5",
        requiresKey = true,
        hasConfigurableEndpoint = false,
        defaultTimeoutSeconds = 120,
        suggestedModels = listOf("claude-opus-5", "claude-sonnet-5", "claude-haiku-4-5"),
    ),

    OpenAi(
        label = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o",
        requiresKey = true,
        hasConfigurableEndpoint = false,
        defaultTimeoutSeconds = 120,
        suggestedModels = listOf("gpt-4o", "gpt-4o-mini", "gpt-4.1", "gpt-4.1-mini"),
    ),

    /**
     * A model running on the reader's own machine, or one they host.
     *
     * No key, because most of these want none - though one is still sent when
     * given, since a server behind a proxy may want it. The model is left to the
     * reader entirely: it names whichever file they have loaded.
     */
    Local(
        label = "Local",
        defaultBaseUrl = "http://localhost:1234/v1",
        defaultModel = "local-model",
        requiresKey = false,
        hasConfigurableEndpoint = true,
        // Long enough to cover a model being read off disk into memory, which
        // is what the first request after choosing one actually waits for and
        // is measured in minutes on a large model - on a machine that is
        // swapping, in many of them.
        defaultTimeoutSeconds = 600,
        suggestedModels = emptyList(),
    ),
    ;

    /**
     * Whether this speaks OpenAI's chat endpoint, which is the thing [OpenAi]
     * and [Local] have in common and the reason there are two providers behind
     * one implementation.
     */
    val isOpenAiCompatible: Boolean get() = this != Anthropic

    companion object {
        val Default = Anthropic
    }
}
