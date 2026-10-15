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

    /** Whether this device has ever caught up with a server - the one sign there is something to forget. */
    val anySet: Boolean get() = Keys.any { read(it) > 0 }

    fun clear() = Keys.forEach { settings.putString(it, "") }

    private fun read(key: String): Long = settings.getString(key)?.toLongOrNull() ?: 0L

    private companion object {
        const val KeyBooks = "sync.cursor.books"
        val Keys = listOf(KeyBooks)
    }
}
