package io.lukia.core.domain.storage

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

    /**
     * Stores [value] under [key], and does nothing at all when that is already
     * what is stored.
     *
     * Part of the contract rather than an implementation's private thrift,
     * because a settings holder saves every one of its keys whenever any changes
     * - see [io.lukia.core.infrastructure.PersistedSettings] - so changing the
     * type size writes eight keys of which seven are unchanged. Each store holds
     * its values in memory, so answering "same as before" is free and is the
     * difference between one write to disk and eight.
     */
    fun putString(key: String, value: String)
}
