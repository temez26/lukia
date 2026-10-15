package io.lukia.features.account.application

import io.lukia.features.account.domain.AccountFailure
import io.lukia.features.account.domain.AccountProblem
import io.lukia.features.account.domain.AccountStatus
import io.lukia.features.account.domain.AccountStore
import io.lukia.features.account.domain.AuthTokens
import io.lukia.features.account.domain.ServerAccount
import io.lukia.features.account.domain.ServerApi
import io.lukia.features.account.domain.ServerInfo
import io.lukia.features.account.domain.serverAddress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * This device's sign-in to a sync server: who it is, and the tokens that prove
 * it. One per app, because there is one account at a time.
 *
 * Signing in is optional and changes nothing until it happens - see
 * docs/specs/sync-server.md. Everything here that talks to the server throws an
 * [AccountFailure]; callers decide what to tell the reader.
 */
class AccountSession(
    private val api: ServerApi,
    private val store: AccountStore,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val _status = MutableStateFlow(statusOf(store.load()?.let { it.account to it.tokens }))
    val status: StateFlow<AccountStatus> = _status.asStateFlow()

    /** One refresh at a time: two would each spend the same refresh token, and the second would end the sign-in. */
    private val refreshing = Mutex()

    /** Checks what the reader typed and asks the server about itself. Answers with the address as it will be used. */
    suspend fun check(typedAddress: String): Pair<String, ServerInfo> {
        val address = serverAddress(typedAddress)
        return address to api.health(address)
    }

    suspend fun signIn(address: String, username: String, password: String) =
        complete(address, api.signIn(address, username.trim(), password))

    /** Creates the server's admin account, for a server nobody has set up yet. */
    suspend fun setUp(address: String, username: String, password: String) =
        complete(address, api.setUp(address, username.trim(), password))

    suspend fun redeem(address: String, code: String, username: String, password: String) =
        complete(address, api.redeem(address, code.trim(), username.trim(), password))

    /**
     * Signs out on this device, whatever the server says. Telling the server is
     * a courtesy that ends the sign-in there too; a server that cannot be
     * reached must not keep somebody signed in on a device they are handing on.
     */
    suspend fun signOut() {
        val stored = store.load()
        store.clear()
        _status.value = AccountStatus.SignedOut

        stored?.tokens?.let { tokens ->
            runCatching { api.signOut(stored.account.address, tokens.accessToken) }
        }
    }

    /**
     * Runs [block] with a current access token, refreshing it first when it is
     * about to run out and once more if the server turns it away. When the
     * refresh itself is refused, the reader is asked to sign in again.
     */
    suspend fun <T> authorized(block: suspend (address: String, accessToken: String) -> T): T {
        val stored = store.load()
        val address = stored?.account?.address
        val current = stored?.tokens
        if (address == null || current == null) throw sessionExpired()
        val tokens = freshTokens(current)

        return try {
            block(address, tokens.accessToken)
        } catch (failure: AccountFailure) {
            if (failure.problem != AccountProblem.SessionExpired) throw failure
            block(address, refresh(tokens).accessToken)
        }
    }

    private suspend fun complete(address: String, tokens: AuthTokens) {
        val info = api.health(address)
        val (username, isAdmin) = api.me(address, tokens.accessToken)
        val account = ServerAccount(address, info.name, username, isAdmin)

        store.save(account, tokens)
        _status.value = AccountStatus.SignedIn(account)
    }

    private suspend fun freshTokens(tokens: AuthTokens): AuthTokens =
        if (tokens.accessExpiresAt - nowMillis() > RefreshAheadMillis) tokens else refresh(tokens)

    private suspend fun refresh(used: AuthTokens): AuthTokens = refreshing.withLock {
        val stored = store.load() ?: throw sessionExpired()
        val current = stored.tokens ?: throw sessionExpired()

        // Somebody else refreshed while this waited for the lock.
        if (current != used) return current

        try {
            api.refresh(stored.account.address, used.refreshToken).also(store::saveTokens)
        } catch (failure: AccountFailure) {
            if (failure.problem == AccountProblem.SessionExpired) {
                store.forgetTokens()
                _status.value = AccountStatus.NeedsSignIn(stored.account)
            }
            throw failure
        }
    }

    private fun sessionExpired() = AccountFailure(AccountProblem.SessionExpired)

    private companion object {
        /** A token with less than this left is refreshed before use rather than sent to expire on the way. */
        const val RefreshAheadMillis = 60_000L

        fun statusOf(stored: Pair<ServerAccount, AuthTokens?>?): AccountStatus = when {
            stored == null -> AccountStatus.SignedOut
            stored.second == null -> AccountStatus.NeedsSignIn(stored.first)
            else -> AccountStatus.SignedIn(stored.first)
        }
    }
}
