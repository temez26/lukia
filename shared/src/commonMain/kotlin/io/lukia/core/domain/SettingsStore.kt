package io.lukia.core.domain

/**
 * A small key-value store for user preferences, backed by whatever each platform
 * already offers - no database and no new dependency, because preferences are a
 * handful of short strings that have to be there the instant a screen opens.
 *
 * Deliberately synchronous: every platform's implementation keeps these in
 * memory once loaded, and a screen that had to await its own settings would draw
 * once with defaults and then visibly change its mind.
 */
interface SettingsStore {

    fun getString(key: String): String?

    fun putString(key: String, value: String)
}
