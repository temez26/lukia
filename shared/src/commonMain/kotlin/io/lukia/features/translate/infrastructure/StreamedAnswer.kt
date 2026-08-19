package io.lukia.features.translate.infrastructure

/**
 * An answer as it accumulates, and whoever is watching it arrive.
 *
 * Both providers stream in pieces that mean nothing on their own, and both have
 * the same two things to get right about them: the pieces have to be joined
 * before anything can be read out of them, and the reader has to be told only
 * when what they would see has actually changed.
 *
 * That second part is the reason this is a type rather than a StringBuilder.
 * Most of what a stream carries changes nothing visible - the opening brace,
 * the key, the note after the sentence has ended, and on one provider the model
 * thinking out loud - and calling back for each of those repaints the page to
 * arrive at the words already on it.
 */
internal class StreamedAnswer(private val onPartial: (String) -> Unit) {

    private val received = StringBuilder()
    private var shown: String? = null

    /** Everything that has arrived, for reading once the stream has ended. */
    val text: String get() = received.toString()

    fun append(piece: String) {
        if (piece.isEmpty()) return

        received.append(piece)

        val sentence = translationSoFar(received.toString()) ?: return

        if (sentence == shown) return

        shown = sentence
        onPartial(sentence)
    }
}
