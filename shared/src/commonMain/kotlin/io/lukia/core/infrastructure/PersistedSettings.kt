package io.lukia.core.infrastructure

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A settings value that is live and written through: held once as a [StateFlow]
 * so every screen reading it sees the same value change at the same moment, and
 * saved whenever it actually does change.
 *
 * The settings holders across the app all wanted this same three-step set - guard,
 * publish, persist - and each was writing it out again. The guard is the step
 * worth having in one place: without it a screen that re-sends the value it is
 * already showing writes to the store on every recomposition.
 *
 * Reading is the caller's job and stays there, because no two of these read alike
 * - one enum, eight keys, a secret store beside the settings file. Only [save] is
 * given here, and only [initial] is asked for.
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
