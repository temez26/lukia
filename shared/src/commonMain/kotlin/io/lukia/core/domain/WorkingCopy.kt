package io.lukia.core.domain

/**
 * A private copy of an imported document, held in app storage.
 *
 * Everything downstream of importing reads this rather than the file the user
 * picked: the reader renders it, and translation will rewrite it. The original
 * is only ever read once, when the copy is made, so nothing the app does can
 * reach back and change the user's own file.
 *
 * [path] is always a plain filesystem path, even where [sourceLocation] is a
 * content:// URI - which is also what lets the platform PDF renderers open it.
 */
data class WorkingCopy(
    val sourceLocation: String,
    val path: String
)
