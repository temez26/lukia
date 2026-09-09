package io.lukia.features.translate.domain

/**
 * The two ways of translating more of the book than the reader pointed at.
 *
 * Both are off until asked for. Everything else here translates what somebody
 * put their finger on: one passage, once, paid for deliberately. These translate
 * a screenful at a time, and the second keeps doing it for as long as the reader
 * scrolls - on a hosted provider real money spent without anybody pressing
 * anything, and on a local model a queue the sentence they actually tapped now
 * waits behind.
 *
 * So they are offered as what they are: useful, expensive, and slower than they
 * look. A reader who has not gone looking for them never finds a button that can
 * spend their afternoon's credit in a scroll.
 */
data class BulkTranslation(
    /** Translating everything on screen, once, when asked. */
    val onScreen: Boolean = false,
    /** Translating what comes into view, until told to stop. */
    val asYouRead: Boolean = false,
) {

    /** Whether either is on, which is whether the reader is offered any of it. */
    val any: Boolean get() = onScreen || asYouRead

    companion object {
        val Default = BulkTranslation()
    }
}
