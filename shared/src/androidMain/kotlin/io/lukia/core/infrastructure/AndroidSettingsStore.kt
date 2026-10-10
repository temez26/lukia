package io.lukia.core.infrastructure

import android.content.Context
import io.lukia.core.domain.storage.SettingsStore

/**
 * Shared preferences: already in memory after the first read, and written back
 * off the calling thread by `apply`.
 */
class AndroidSettingsStore(context: Context) : SettingsStore {

    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun getString(key: String): String? = preferences.getString(key, null)

    override fun putString(key: String, value: String) {
        // Already-stored values are left alone - see [SettingsStore.putString].
        // `apply` is off-thread but not free: each one is a commit queued behind
        // the last, and the queue is drained synchronously when the activity
        // pauses.
        if (preferences.getString(key, null) == value) return

        preferences.edit().putString(key, value).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "io.lukia.settings"
    }
}
