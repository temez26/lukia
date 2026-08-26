package io.lukia.core.domain

/**
 * A document another app handed to Lukia - a PDF opened from Files, a book
 * shared out of a mail message, an EPUB tapped in a browser's downloads.
 *
 * Distinct from [PickedDocument], which is a document the reader went and
 * fetched through the system picker. The difference is not where it came from
 * but how long it can be read: a picked document is addressed by a location the
 * app asked permission to keep, where this one arrives with a grant that lasts
 * as long as the screen that delivered it. Nothing here may be stored against
 * the library, because by the time the library is next opened none of it will
 * resolve.
 *
 * [read] is therefore the whole point of this type. It is called once, while the
 * grant is still good, and what it returns is copied into storage the app owns -
 * see [IncomingDocumentStore].
 */
class IncomingDocument(
    /** What the sending app calls it, extension included. */
    val fileName: String,
    val read: suspend () -> ByteArray?,
) {

    /**
     * Taken from the name rather than from the sender's declared type. A type is
     * what an app believes it is sending and is wrong often enough to matter -
     * see the mime list in `PlatformFilePicker.android.kt` - while the extension
     * is what the reader themselves would call it.
     */
    val extension: String get() = fileName.substringAfterLast('.', "")
}

/**
 * Where a document handed to the app is kept so that it survives the grant it
 * arrived under.
 *
 * Copying rather than referencing is not a preference here. On Android an
 * `ACTION_VIEW` grant cannot be made persistable at all, and on iOS an opened
 * URL is security-scoped; a library entry pointing at either would open once and
 * then fail for good, with nothing on the shelf to say why. This is also why
 * [DocumentWorkspace]'s rule - copy on first open, not on import - does not
 * reach these: there is nothing to copy from later.
 */
interface IncomingDocumentStore {

    /**
     * Keeps [document] as the app's own, and answers it as something the import
     * can take from here exactly like a picked one.
     *
     * Throws if it cannot be read or cannot be written, both of which leave the
     * library untouched rather than half-added-to.
     */
    suspend fun keep(document: IncomingDocument): PickedDocument
}
