package io.lukia.core.infrastructure

import io.lukia.core.domain.document.IncomingDocument
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The hand-off between the platform delivering a document and the library
 * importing it.
 *
 * The two ends cannot see each other: a document arrives in
 * `Activity.onNewIntent` or a SwiftUI `onOpenURL`, outside the composition and
 * often before there is one, while what imports it is a ViewModel scoped to a
 * screen that may not exist yet.
 *
 * A channel rather than a [kotlinx.coroutines.flow.StateFlow] or replaying
 * shared flow, because nothing may be lost or delivered twice. A book delivered
 * during a cold start is queued until the library collects - no-replay would
 * drop it, replay would re-import on every resubscribe - and a channel hands
 * each document to exactly one collector, so a rotation (which rebuilds the
 * composition but not the process) does not shelve the same book again.
 */
class DocumentInbox {

    /**
     * Dropping the oldest rather than suspending: [deliver] is called from the
     * platform's own callback, which cannot wait, and somebody who has shared
     * more than this many books at once before the library has started is
     * better served losing the first than having the app stall on the intent.
     */
    private val delivered = Channel<IncomingDocument>(
        capacity = CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Consumed by the library. Each document reaches exactly one collector. */
    val documents: Flow<IncomingDocument> = delivered.receiveAsFlow()

    fun deliver(document: IncomingDocument) {
        delivered.trySend(document)
    }

    private companion object {
        const val CAPACITY = 32
    }
}
