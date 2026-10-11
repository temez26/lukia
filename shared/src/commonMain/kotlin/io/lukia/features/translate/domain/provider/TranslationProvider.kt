package io.lukia.features.translate.domain.provider

/**
 * Who does the translating.
 *
 * Three entries rather than two implementations: [OpenAi] and [Local] speak the
 * same wire format, since everything that runs a model locally - LM Studio,
 * Ollama, llama.cpp's server, vLLM - exposes OpenAI's chat endpoint. They are
 * apart only because they are configured and fail differently, and "your key was
 * rejected" is no help to somebody who has no key.
 */
enum class TranslationProvider(
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
     * Silence rather than total duration, because the two fail on opposite
     * scales: a hosted service silent for a minute is not going to answer, while
     * a local server spends minutes reading an unloaded model off disk before it
     * can say a word. One number for both either hangs the hosted request or
     * abandons the local one while it is still working.
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
        defaultBaseUrl = "https://api.anthropic.com/v1",
        defaultModel = "claude-opus-5",
        requiresKey = true,
        hasConfigurableEndpoint = false,
        defaultTimeoutSeconds = 120,
        suggestedModels = listOf("claude-opus-5", "claude-sonnet-5", "claude-haiku-4-5"),
    ),

    OpenAi(
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o",
        requiresKey = true,
        hasConfigurableEndpoint = false,
        defaultTimeoutSeconds = 120,
        // Newest first. The default stays where it is on purpose: it is what
        // every reader who has never opened this list is already using, and
        // moving it would re-ask for every sentence they have already paid for
        // - the model is part of what a translation is filed under.
        suggestedModels = listOf("gpt-5", "gpt-5-mini", "gpt-4.1", "gpt-4o", "gpt-4o-mini"),
    ),

    /**
     * A model running on the reader's own machine, or one they host.
     *
     * No key, because most of these want none - though one is still sent when
     * given, since a server behind a proxy may want it. The model is left to the
     * reader entirely: it names whichever file they have loaded.
     */
    Local(
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

    companion object {
        val Default = Anthropic
    }
}
