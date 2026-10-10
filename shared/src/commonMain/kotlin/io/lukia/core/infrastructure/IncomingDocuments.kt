package io.lukia.core.infrastructure

import io.lukia.core.domain.document.IncomingDocumentStore

/**
 * Books other apps hand to Lukia: where they arrive, and where they are kept.
 *
 * The two travel together everywhere they are used and are meaningless apart -
 * an inbox nothing copies out of delivers documents that stop being readable,
 * and a store nothing fills is never called. Passing them as one is what lets
 * the library take delivery as a single collaborator rather than as two
 * constructor arguments that have to be kept in step by whoever wires them.
 */
class IncomingDocuments(
    val inbox: DocumentInbox,
    val store: IncomingDocumentStore,
)
