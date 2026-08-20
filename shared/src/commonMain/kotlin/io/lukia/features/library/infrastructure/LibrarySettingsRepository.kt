package io.lukia.features.library.infrastructure

import io.lukia.core.domain.SettingsStore
import io.lukia.core.infrastructure.readEnum
import io.lukia.features.library.presentation.LibraryLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    private val _layout = MutableStateFlow(
        store.readEnum(KEY_LAYOUT, LibraryLayout.entries, LibraryLayout.Grid),
    )
    val layout: StateFlow<LibraryLayout> = _layout.asStateFlow()

    fun setLayout(layout: LibraryLayout) {
        if (_layout.value == layout) return

        _layout.value = layout
        store.putString(KEY_LAYOUT, layout.name)
    }

    private companion object {
        const val KEY_LAYOUT = "library.layout"
    }
}
