package io.lukia.features.account

import io.lukia.features.account.domain.AccountFailure
import io.lukia.features.account.domain.AccountProblem
import io.lukia.features.account.domain.AccountStore
import io.lukia.features.account.domain.AuthTokens
import io.lukia.features.account.domain.LastSignIn
import io.lukia.features.account.domain.Person
import io.lukia.features.account.domain.ServerAccount
import io.lukia.features.account.domain.ServerApi
import io.lukia.features.account.domain.ServerInfo
import io.lukia.features.account.domain.StoredAccount

internal const val HomeServer = "http://192.168.1.10:8080"

internal fun tokens(n: Int, accessExpiresAt: Long = Long.MAX_VALUE) =
    AuthTokens("access-$n", accessExpiresAt, "refresh-$n", Long.MAX_VALUE)

/**
 * A server that knows one account, `alice` with password `correct horse`, and
 * hands out numbered tokens so a test can tell which sign-in or refresh it is
 * looking at.
 */
internal class FakeServerApi(
    var info: ServerInfo = ServerInfo("Home shelf", "1.0.1", setupRequired = false),
) : ServerApi {
    var reachable = true
    var issued = 0
    var refreshes = 0
    var signOuts = 0
    var refreshRejected = false
    val validAccess = mutableSetOf<String>()
    private val validRefresh = mutableSetOf<String>()

    override suspend fun health(address: String): ServerInfo = reachable().let { info }

    override suspend fun setUp(address: String, username: String, password: String): AuthTokens {
        reachable()
        if (!info.setupRequired) throw AccountFailure(AccountProblem.AlreadySetUp)
        return issue()
    }

    override suspend fun signIn(address: String, username: String, password: String): AuthTokens {
        reachable()
        if (username != "alice" || password != "correct horse") throw AccountFailure(AccountProblem.SignInRejected)
        return issue()
    }

    var signUps = 0

    override suspend fun signUp(address: String, username: String, password: String): AuthTokens {
        reachable()
        if (info.signUpOpen != true) throw AccountFailure(AccountProblem.SignUpClosed)
        signUps++
        return issue()
    }

    override suspend fun redeem(address: String, code: String, username: String, password: String): AuthTokens {
        reachable()
        if (code != "invite") throw AccountFailure(AccountProblem.InvalidInvite)
        return issue()
    }

    override suspend fun refresh(address: String, refreshToken: String): AuthTokens {
        reachable()
        refreshes++
        if (refreshRejected || !validRefresh.remove(refreshToken)) throw AccountFailure(AccountProblem.SessionExpired)
        return issue()
    }

    override suspend fun signOut(address: String, accessToken: String) {
        reachable()
        signOuts++
    }

    var deleted = 0
    var onlyAdmin = false

    override suspend fun deleteAccount(address: String, accessToken: String) {
        reachable()
        if (accessToken !in validAccess) throw AccountFailure(AccountProblem.SessionExpired)
        if (onlyAdmin) throw AccountFailure(AccountProblem.LastAdmin)
        deleted++
    }

    var signUpSetting = false
    var notAdmin = false

    override suspend fun signUpOpen(address: String, accessToken: String): Boolean {
        reachable()
        if (notAdmin) throw AccountFailure(AccountProblem.NotAdmin)
        return signUpSetting
    }

    override suspend fun setSignUpOpen(address: String, accessToken: String, open: Boolean): Boolean {
        reachable()
        if (notAdmin) throw AccountFailure(AccountProblem.NotAdmin)
        signUpSetting = open
        return signUpSetting
    }

    var people = emptyList<Person>()

    override suspend fun people(address: String, accessToken: String): List<Person> {
        reachable()
        if (notAdmin) throw AccountFailure(AccountProblem.NotAdmin)
        return people
    }

    val passwordResets = mutableListOf<Pair<String, String>>()

    override suspend fun resetPassword(address: String, accessToken: String, username: String, password: String) {
        reachable()
        if (notAdmin) throw AccountFailure(AccountProblem.NotAdmin)
        passwordResets += username to password
    }

    override suspend fun removeAccount(address: String, accessToken: String, username: String) {
        reachable()
        if (notAdmin) throw AccountFailure(AccountProblem.NotAdmin)
        people = people.filterNot { it.username == username }
    }

    override suspend fun me(address: String, accessToken: String): Pair<String, Boolean> {
        reachable()
        if (accessToken !in validAccess) throw AccountFailure(AccountProblem.SessionExpired)
        return "alice" to false
    }

    /** What a protected endpoint does with a token. */
    fun use(accessToken: String): String {
        if (accessToken !in validAccess) throw AccountFailure(AccountProblem.SessionExpired)
        return "ok with $accessToken"
    }

    private fun issue(): AuthTokens = tokens(++issued).also {
        validAccess += it.accessToken
        validRefresh += it.refreshToken
    }

    private fun reachable() {
        if (!reachable) throw AccountFailure(AccountProblem.ServerUnreachable)
    }
}

internal class InMemoryAccountStore(private var stored: StoredAccount? = null) : AccountStore {
    private var last: LastSignIn? = null

    override fun load() = stored

    override fun save(account: ServerAccount, tokens: AuthTokens) {
        stored = StoredAccount(account, tokens)
        last = LastSignIn(account.address, account.username)
    }

    override fun lastSignIn() = last

    override fun saveTokens(tokens: AuthTokens) {
        stored = stored?.copy(tokens = tokens)
    }

    override fun forgetTokens() {
        stored = stored?.copy(tokens = null)
    }

    override fun clear() {
        stored = null
    }
}
