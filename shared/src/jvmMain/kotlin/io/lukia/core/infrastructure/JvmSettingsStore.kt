package io.lukia.core.infrastructure

import io.lukia.core.domain.SettingsStore
import java.io.File
import java.util.Properties

/**
 * A properties file beside the desktop build's other stored state.
 *
 * Preferred over `java.util.prefs`, which on Windows writes to the registry -
 * an odd place to leave a reading preference, and one the user cannot clear by
 * deleting the app's directory.
 *
 * Values are held in memory; the file is read once and rewritten on each change,
 * which is fine for a handful of short keys.
 */
class JvmSettingsStore(
    /** Injectable so tests can work in a temporary directory. */
    private val file: File = File(lukiaAppDirectory(), FILE_NAME),
) : SettingsStore {

    private val properties: Properties by lazy {
        Properties().apply {
            if (file.isFile) {
                runCatching { file.inputStream().use { load(it) } }
            }
        }
    }

    override fun getString(key: String): String? = properties.getProperty(key)

    override fun putString(key: String, value: String) {
        properties.setProperty(key, value)

        // A preference is not worth failing a screen over: losing one between
        // runs is a far better outcome than a write error reaching the reader.
        runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { properties.store(it, COMMENT) }
        }
    }

    private companion object {
        const val FILE_NAME = "settings.properties"
        const val COMMENT = "Lukia settings"
    }
}
