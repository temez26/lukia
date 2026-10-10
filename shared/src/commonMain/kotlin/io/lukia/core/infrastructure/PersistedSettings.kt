package io.lukia.core.infrastructure

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A settings value that is live and written through: held once as a [StateFlow]
 * so every screen reading it sees the same value change at the same moment, and
 * saved whenever it actually does change.
 *
 * The settings holders all wanted the same three-step set - guard, publish,
 * persist - and each wrote it out again. The guard is the step worth sharing:
 * without it a screen that re-sends the value it is already showing writes to
 * the store on every recomposition.
 *
 * Reading stays the caller's job, because no two of these read alike - one enum,
 * eight keys, a secret store beside the settings file.
 */
class PersistedSettings<T>(
    initial: T,
    private val save: (T) -> Unit,
) {

    private val _state = MutableStateFlow(initial)
    val state: StateFlow<T> = _state.asStateFlow()

    /** Does nothing at all for a value equal to the one already held. */
    fun set(value: T) {
        if (_state.value == value) return

        _state.value = value
        save(value)
    }
}
