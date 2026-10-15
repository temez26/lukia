package io.lukia.features.sync.infrastructure

import io.lukia.core.domain.storage.SettingsStore

/**
 * Whether the reading this device recorded before it first signed in goes to
 * the account - asked once per device, and answered for good.
 *
 * Not cleared by signing out: the question is about this device's past, and it
 * has been answered.
 */
class HistoryChoice(private val settings: SettingsStore) {

    var decided: Boolean
        get() = settings.getString(KeyDecided) == "true"
        set(value) = settings.putString(KeyDecided, if (value) "true" else "")

    /**
     * The last sitting there was when the question was first asked. Keeping the
     * history on the device keeps these; anything read after it goes to the
     * account either way.
     */
    var askedUpTo: Long
        get() = settings.getString(KeyAskedUpTo)?.toLongOrNull() ?: 0L
        set(value) = settings.putString(KeyAskedUpTo, value.toString())

    private companion object {
        const val KeyDecided = "sync.history.decided"
        const val KeyAskedUpTo = "sync.history.askedUpTo"
    }
}
