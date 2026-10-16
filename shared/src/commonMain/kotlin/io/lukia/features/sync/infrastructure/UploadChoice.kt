package io.lukia.features.sync.infrastructure

import io.lukia.core.domain.storage.SettingsStore

/**
 * Whether books imported on this device go to the server's shared library on
 * their own. On unless the reader turns it off; kept through signing out, as a
 * preference about this device rather than about one account.
 */
class UploadChoice(private val settings: SettingsStore) {

    var enabled: Boolean
        get() = settings.getString(KeyEnabled) != "false"
        set(value) = settings.putString(KeyEnabled, value.toString())

    private companion object {
        const val KeyEnabled = "sync.autoUpload"
    }
}
