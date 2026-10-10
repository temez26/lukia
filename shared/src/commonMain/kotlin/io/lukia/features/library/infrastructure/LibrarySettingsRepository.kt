package io.lukia.features.library.infrastructure

import io.lukia.core.domain.storage.SettingsStore
import io.lukia.core.infrastructure.PersistedSettings
import io.lukia.core.infrastructure.readEnum
import io.lukia.features.library.domain.LibraryLayout
import kotlinx.coroutines.flow.StateFlow

/**
 * What the reader has chosen about the shelf itself, live and persisted.
 *
 * Apart from the query beside it, which is not remembered at all: a filter is
 * about what somebody is looking for right now and should be gone next time,
 * while how the shelf is laid out follows how big their library is - see
 * [LibraryLayout].
 */
class LibrarySettingsRepository(
    private val store: SettingsStore,
) {

    private val persisted = PersistedSettings(
        store.readEnum(KEY_LAYOUT, LibraryLayout.entries, LibraryLayout.Grid),
    ) { store.putString(KEY_LAYOUT, it.name) }

    val layout: StateFlow<LibraryLayout> = persisted.state

    fun setLayout(layout: LibraryLayout) = persisted.set(layout)

    private companion object {
        const val KEY_LAYOUT = "library.layout"
    }
}
