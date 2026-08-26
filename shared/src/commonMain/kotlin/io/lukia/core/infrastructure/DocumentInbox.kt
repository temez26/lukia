package io.lukia.core.infrastructure

import io.lukia.core.domain.IncomingDocument
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The hand-off between the platform delivering a document and the library
 * importing it.
 *
 * It exists because the two ends cannot see each other. A document arrives in
 * `Activity.onNewIntent` or a SwiftUI `onOpenURL` - outside the composition
 * entirely, and often before there is one - while the thing that imports it is a
 * ViewModel scoped to a screen that may not have been built yet.
 *
 * A channel rather than a [kotlinx.coroutines.flow.StateFlow] or a replaying
 * shared flow, for two reasons that both matter here. Nothing may be lost: a
 * book delivered during a cold start is queued until the library starts
 * collecting, where a flow with no replay would drop it and a flow with replay
 * would re-import it on every resubscribe. And nothing may be delivered twice: a
 * channel hands each document to exactly one collector, so a rotation - which
 * rebuilds the composition but not the process - does not add the same book to
 * the shelf again.
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
