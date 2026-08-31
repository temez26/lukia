package io.lukia.core.infrastructure

import io.lukia.core.domain.storage.SettingsStore

/**
 * Reads an enum setting by name.
 *
 * By name rather than by ordinal, so the order of an enum's entries is free to
 * change without silently rewriting what everyone had chosen.
 *
 * A value that cannot be read back - written by an older build, or edited by hand
 * into nonsense - falls back to [default] rather than throwing, so one bad key
 * cannot take a whole settings screen down with it.
 */
fun <T : Enum<T>> SettingsStore.readEnum(
    key: String,
    entries: List<T>,
    default: T,
): T {
    val stored = getString(key) ?: return default
    return entries.firstOrNull { it.name == stored } ?: default
}
