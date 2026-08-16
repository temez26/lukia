package io.lukia.core.infrastructure

import io.lukia.core.domain.SettingsStore
import platform.Foundation.NSUserDefaults

/**
 * User defaults: the system's own place for exactly this, in memory once loaded
 * and persisted by the system on its own schedule.
 *
 * Keys are prefixed because user defaults is a single namespace shared with
 * anything else the app or its frameworks store.
 */
class IOSSettingsStore : SettingsStore {

    private val defaults = NSUserDefaults.standardUserDefaults

    override fun getString(key: String): String? = defaults.stringForKey(prefixed(key))

    override fun putString(key: String, value: String) {
        defaults.setObject(value, prefixed(key))
    }

    private fun prefixed(key: String): String = "$KEY_PREFIX$key"

    private companion object {
        const val KEY_PREFIX = "io.lukia."
    }
}
