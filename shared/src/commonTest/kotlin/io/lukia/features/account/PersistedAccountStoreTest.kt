package io.lukia.features.account

import io.lukia.core.domain.storage.FakeSecretStore
import io.lukia.core.domain.storage.FakeSettingsStore
import io.lukia.features.account.domain.ServerAccount
import io.lukia.features.account.domain.StoredAccount
import io.lukia.features.account.infrastructure.PersistedAccountStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class PersistedAccountStoreTest {

    private val settings = FakeSettingsStore()
    private val secrets = FakeSecretStore()
    private val store = PersistedAccountStore(settings, secrets)
    private val account = ServerAccount(HomeServer, "Home shelf", "alice", isAdmin = true)

    @Test
    fun anAccountComesBackAsItWasSaved() {
        store.save(account, tokens(1, accessExpiresAt = 42))

        assertEquals(
            StoredAccount(account, tokens(1, accessExpiresAt = 42)),
            PersistedAccountStore(settings, secrets).load(),
        )
    }

    @Test
    fun tokensAreKeptOnlyInTheSecretStore() {
        store.save(account, tokens(1))

        assertFalse(settings.values.values.any { "access-1" in it || "refresh-1" in it })
    }

    @Test
    fun forgettingTheTokensKeepsTheAccount() {
        store.save(account, tokens(1))

        store.forgetTokens()

        assertEquals(StoredAccount(account, tokens = null), store.load())
    }

    @Test
    fun clearingLeavesNothing() {
        store.save(account, tokens(1))

        store.clear()

        assertNull(store.load())
        assertNull(secrets.getSecret("account.refreshToken"))
    }
}
