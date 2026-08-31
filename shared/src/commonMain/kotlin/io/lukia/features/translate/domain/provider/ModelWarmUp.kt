package io.lukia.features.translate.domain.provider

/**
 * Asks the configured server to have its model ready.
 *
 * This exists for the local case and only for it. A hosted service holds every
 * model it offers loaded; a server on the reader's own machine holds one, and
 * choosing a different one means it is read off disk on the next request -
 * which is seconds for a small model and minutes for a large one.
 *
 * Paid at the moment the reader picks the model, where a wait is expected and
 * has somewhere to be shown, rather than on the first sentence they tap in the
 * middle of a chapter.
 *
 * A port of its own rather than a method on [Translator] for the same reason
 * [ModelCatalogue] is: it is asked once while setting things up, by the screen
 * that does the setting up, and a translator that cannot answer it translates
 * perfectly well.
 */
fun interface ModelWarmUp {

    /**
     * Returns once the server has the model ready, or throws with why it could
     * not. Sends real work rather than a ping, because a server only loads a
     * model when it is asked to run one.
     */
    suspend fun warmUp()
}
