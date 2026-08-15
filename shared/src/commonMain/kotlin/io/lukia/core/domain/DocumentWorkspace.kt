package io.lukia.core.domain

/**
 * Owns the app's private copies of imported documents.
 *
 * Copies are made when a document is first opened rather than when it is
 * imported: a folder import can land hundreds of books, and duplicating every
 * one of them for the sake of the few that get read would double the library's
 * disk footprint for nothing.
 */
interface DocumentWorkspace {

    /**
     * The working copy of [document], made now if this is the first time it has
     * been opened and reused otherwise.
     *
     * Throws if the original can no longer be read - a copy that does not
     * faithfully mirror the source is worse than no copy at all, since
     * everything downstream trusts it.
     */
    suspend fun workingCopy(document: DocumentInfo): WorkingCopy

    /**
     * Deletes the working copy of the document at [location], if there is one.
     * A no-op when there is not, so removing a book that was never opened is
     * not a special case for callers.
     */
    suspend fun discard(location: String)
}
