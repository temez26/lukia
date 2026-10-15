package io.lukia.features.account.infrastructure

import io.lukia.core.domain.storage.SecretStore
import io.lukia.core.domain.storage.SettingsStore
import io.lukia.features.account.domain.AccountStore
import io.lukia.features.account.domain.AuthTokens
import io.lukia.features.account.domain.LastSignIn
import io.lukia.features.account.domain.ServerAccount
import io.lukia.features.account.domain.StoredAccount

/**
 * The account in settings and its tokens in the secret store, which is the
 * split the translator's API keys already make - see [SecretStore]. A token is
 * as good as the password for as long as it lasts, so it gets the keychain.
 *
 * A blank setting is an absent one: the settings store has no way to remove a
 * key, so clearing writes nothing.
 */
class PersistedAccountStore(
    private val settings: SettingsStore,
    private val secrets: SecretStore,
) : AccountStore {

    override fun load(): StoredAccount? {
        val address = settings.read(KeyAddress) ?: return null
        val account = ServerAccount(
            address = address,
            serverName = settings.read(KeyServerName).orEmpty(),
            username = settings.read(KeyUsername).orEmpty(),
            isAdmin = settings.read(KeyIsAdmin).toBoolean(),
        )
        return StoredAccount(account, loadTokens())
    }

    override fun save(account: ServerAccount, tokens: AuthTokens) {
        settings.putString(KeyAddress, account.address)
        settings.putString(KeyServerName, account.serverName)
        settings.putString(KeyUsername, account.username)
        settings.putString(KeyIsAdmin, account.isAdmin.toString())
        settings.putString(KeyLastAddress, account.address)
        settings.putString(KeyLastUsername, account.username)
        saveTokens(tokens)
    }

    override fun saveTokens(tokens: AuthTokens) {
        secrets.putSecret(KeyAccessToken, tokens.accessToken)
        secrets.putSecret(KeyRefreshToken, tokens.refreshToken)
        settings.putString(KeyAccessExpiresAt, tokens.accessExpiresAt.toString())
        settings.putString(KeyRefreshExpiresAt, tokens.refreshExpiresAt.toString())
    }

    override fun forgetTokens() {
        secrets.removeSecret(KeyAccessToken)
        secrets.removeSecret(KeyRefreshToken)
        settings.putString(KeyAccessExpiresAt, "")
        settings.putString(KeyRefreshExpiresAt, "")
    }

    override fun clear() {
        forgetTokens()
        listOf(KeyAddress, KeyServerName, KeyUsername, KeyIsAdmin).forEach { settings.putString(it, "") }
    }

    override fun lastSignIn(): LastSignIn? {
        val address = settings.read(KeyLastAddress) ?: return null
        return LastSignIn(address, settings.read(KeyLastUsername).orEmpty())
    }

    private fun loadTokens(): AuthTokens? {
        val access = secrets.getSecret(KeyAccessToken)?.takeIf { it.isNotBlank() } ?: return null
        val refresh = secrets.getSecret(KeyRefreshToken)?.takeIf { it.isNotBlank() } ?: return null

        return AuthTokens(
            accessToken = access,
            accessExpiresAt = settings.read(KeyAccessExpiresAt)?.toLongOrNull() ?: 0L,
            refreshToken = refresh,
            refreshExpiresAt = settings.read(KeyRefreshExpiresAt)?.toLongOrNull() ?: 0L,
        )
    }

    private fun SettingsStore.read(key: String): String? = getString(key)?.takeIf { it.isNotBlank() }

    private companion object {
        const val KeyAddress = "account.address"
        const val KeyServerName = "account.serverName"
        const val KeyUsername = "account.username"
        const val KeyIsAdmin = "account.isAdmin"
        const val KeyAccessExpiresAt = "account.accessExpiresAt"
        const val KeyRefreshExpiresAt = "account.refreshExpiresAt"
        const val KeyAccessToken = "account.accessToken"
        const val KeyRefreshToken = "account.refreshToken"

        // Apart from the keys above, which signing out clears.
        const val KeyLastAddress = "account.last.address"
        const val KeyLastUsername = "account.last.username"
    }
}
