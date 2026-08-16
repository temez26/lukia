package io.lukia.core.domain

/**
 * Stands in for the platform stores, which all need real device storage.
 * Counts writes so tests can tell a saved change from a no-op.
 */
class FakeSettingsStore(
    initialValues: Map<String, String> = emptyMap()
) : SettingsStore {

    val values = initialValues.toMutableMap()
    var writeCount = 0
        private set

    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String) {
        values[key] = value
        writeCount++
    }
}
