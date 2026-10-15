package io.lukia.features.sync.infrastructure

import io.lukia.core.domain.storage.SettingsStore

/**
 * How far this device has caught up with the server, per kind of change - the
 * `since` it asks from next. See `ChangePage`.
 *
 * Zero, meaning everything, until the first catch-up; cleared on signing out so
 * the next account, or the same one on a server rebuilt from scratch, starts
 * from the beginning rather than from a number that meant something elsewhere.
 */
class SyncCursors(private val settings: SettingsStore) {

    fun books(): Long = read(KeyBooks)

    fun setBooks(cursor: Long) = settings.putString(KeyBooks, cursor.toString())

    fun bookState(): Long = read(KeyBookState)

    fun setBookState(cursor: Long) = settings.putString(KeyBookState, cursor.toString())

    fun sessions(): Long = read(KeySessions)

    fun setSessions(cursor: Long) = settings.putString(KeySessions, cursor.toString())

    /**
     * Whether what the device knew before signing in has been queued for the
     * account yet - once per sign-in, see `BookStateStore.snapshot`.
     */
    var snapshotTaken: Boolean
        get() = settings.getString(KeySnapshot) == "true"
        set(value) = settings.putString(KeySnapshot, if (value) "true" else "")

    /**
     * Where the library ended when this device signed in: books added after it
     * are the new ones that go to the server on their own. Null until the first
     * round after signing in sets it.
     */
    var uploadsAfter: Long?
        get() = settings.getString(KeyUploadsAfter)?.toLongOrNull()
        set(value) = settings.putString(KeyUploadsAfter, value?.toString().orEmpty())

    /** New books the server refused - too large, or not a book after all - and that are not tried again. */
    var refusedUploads: Set<String>
        get() = settings.getString(KeyRefusedUploads)?.split('\n')?.filter { it.isNotEmpty() }?.toSet().orEmpty()
        set(value) = settings.putString(KeyRefusedUploads, value.joinToString("\n"))

    /** Whether this device has ever caught up with a server - the one sign there is something to forget. */
    val anySet: Boolean get() = Keys.any { read(it) > 0 } || snapshotTaken

    fun clear() = (Keys + KeySnapshot + KeyUploadsAfter + KeyRefusedUploads).forEach { settings.putString(it, "") }

    private fun read(key: String): Long = settings.getString(key)?.toLongOrNull() ?: 0L

    private companion object {
        const val KeyBooks = "sync.cursor.books"
        const val KeyBookState = "sync.cursor.bookState"
        const val KeySessions = "sync.cursor.sessions"
        const val KeySnapshot = "sync.snapshotTaken"
        const val KeyUploadsAfter = "sync.uploadsAfter"
        const val KeyRefusedUploads = "sync.refusedUploads"
        val Keys = listOf(KeyBooks, KeyBookState, KeySessions)
    }
}
