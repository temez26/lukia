package io.lukia.features.translate.domain

/**
 * The two ways of translating more of the book than the reader pointed at.
 *
 * Both are off until asked for, which is not how a finished feature is shipped
 * and is the right way to ship these two. Everything else here translates what
 * somebody put their finger on: one passage, once, paid for deliberately. These
 * translate a screenful at a time, and the second one keeps doing it for as long
 * as the reader scrolls - which on a hosted provider is real money spent without
 * anybody pressing anything, and on a model running locally is a queue that the
 * sentence they actually tapped is now waiting behind.
 *
 * So they are offered, and they are offered as what they are: useful, expensive,
 * and slower than they look. A reader who has not gone looking for them never
 * finds a button that can spend their afternoon's credit in a scroll.
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
