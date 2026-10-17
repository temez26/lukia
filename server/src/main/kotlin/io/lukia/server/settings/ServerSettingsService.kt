package io.lukia.server.settings

import io.lukia.api.ServerSettings
import io.lukia.server.db.ServerDatabase

/**
 * What the admin has decided about this server, read and changed from the app.
 *
 * Blocking - JDBC is - so routes call it through `blocking`.
 */
class ServerSettingsService(private val database: ServerDatabase) {

    private val settings get() = database.serverSettingQueries

    /**
     * Whether anyone may create an account. Closed until the admin opens it: a
     * server is often reachable from the whole internet, and an open one lets
     * whoever finds the address read and add to the shared library.
     */
    fun signUpOpen(): Boolean = settings.selectValue(SignUpOpen).executeAsOneOrNull() == true.toString()

    fun current(): ServerSettings = ServerSettings(signUpOpen = signUpOpen())

    fun update(changed: ServerSettings): ServerSettings {
        settings.setValue(SignUpOpen, changed.signUpOpen.toString())
        return current()
    }

    private companion object {
        const val SignUpOpen = "signUpOpen"
    }
}
