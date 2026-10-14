package io.lukia.server.accounts

import io.ktor.http.HttpStatusCode
import io.lukia.api.AccountRules
import io.lukia.api.ApiErrorCodes
import io.lukia.api.Credentials
import io.lukia.api.InviteResponse
import io.lukia.api.RedeemInviteRequest
import io.lukia.api.TokenResponse
import io.lukia.server.db.ServerDatabase
import io.lukia.server.http.ApiException
import java.sql.SQLException
import java.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * Making, signing in to and removing accounts.
 *
 * Blocking throughout - JDBC and Argon2 both are - so routes call it through
 * `blocking`.
 */
class AccountService(
    private val database: ServerDatabase,
    private val hasher: PasswordHasher,
    private val tokens: TokenService,
    private val secrets: SecretTokens,
    private val throttle: SignInThrottle,
    private val clock: Clock,
) {
    private val accounts get() = database.accountQueries

    /**
     * A hash of no real password, verified against when the username is unknown
     * so that a missing account takes as long to refuse as a wrong password -
     * otherwise the time taken would say which usernames exist.
     */
    private val decoyHash by lazy { hasher.hash(secrets.generate()) }

    fun setup(credentials: Credentials): TokenResponse {
        validate(credentials.username, credentials.password)
        val passwordHash = hasher.hash(credentials.password)

        return database.transactionWithResult {
            accounts.insertFirst(credentials.username, passwordHash, clock.millis())
            if (accounts.changes().executeAsOne() == 0L) {
                throw ApiException(HttpStatusCode.Conflict, ApiErrorCodes.SetupDone, "This server is already set up.")
            }
            tokens.issue(accounts.lastInsertId().executeAsOne())
        }
    }

    fun signIn(credentials: Credentials, address: String): TokenResponse {
        throttle.check(credentials.username, address)

        val account = accounts.selectByUsername(credentials.username).executeAsOneOrNull()
        val matches = hasher.verify(credentials.password, account?.passwordHash ?: decoyHash)

        if (account == null || !matches) {
            throttle.recordFailure(credentials.username, address)
            throw ApiException(
                HttpStatusCode.Unauthorized,
                ApiErrorCodes.InvalidCredentials,
                "That username and password do not match an account.",
            )
        }

        throttle.clear(credentials.username)
        return tokens.issue(account.id)
    }

    fun createInvite(admin: SignedInAccount): InviteResponse {
        val now = clock.millis()
        val code = secrets.generate()
        val expiresAt = now + InviteLifetime.inWholeMilliseconds

        database.inviteQueries.deleteExpired(now)
        database.inviteQueries.insert(secrets.hash(code), admin.id, expiresAt)

        return InviteResponse(code, expiresAt)
    }

    /** The account and the spending of its invite happen together or not at all. */
    fun redeem(request: RedeemInviteRequest): TokenResponse {
        validate(request.username, request.password)
        requireFree(request.username)
        val passwordHash = hasher.hash(request.password)

        return database.transactionWithResult {
            val now = clock.millis()
            database.inviteQueries.consume(secrets.hash(request.code), now)
            if (accounts.changes().executeAsOne() == 0L) {
                throw ApiException(
                    HttpStatusCode.BadRequest,
                    ApiErrorCodes.InvalidInvite,
                    "That invite code is unknown, used or expired.",
                )
            }

            insertAccount(request.username, passwordHash, now)
            tokens.issue(accounts.lastInsertId().executeAsOne())
        }
    }

    fun delete(account: SignedInAccount) {
        database.transaction {
            if (account.isAdmin && accounts.countAdmins().executeAsOne() <= 1L) {
                throw ApiException(
                    HttpStatusCode.Conflict,
                    ApiErrorCodes.LastAdmin,
                    "The only admin cannot delete their account.",
                )
            }
            accounts.deleteById(account.id)
        }
    }

    /** Signs the account out everywhere: whoever needed the reset may not be the only one with the old password. */
    fun resetPassword(username: String, password: String) {
        validatePassword(password)
        val account = accounts.selectByUsername(username).executeAsOneOrNull()
            ?: throw ApiException(HttpStatusCode.NotFound, ApiErrorCodes.NotFound, "No account by that name.")
        val passwordHash = hasher.hash(password)

        database.transaction {
            accounts.setPasswordHash(passwordHash, account.id)
            tokens.revokeAll(account.id)
        }
    }

    private fun requireFree(username: String) {
        if (accounts.selectByUsername(username).executeAsOneOrNull() != null) throw usernameTaken()
    }

    /** Checked again by the UNIQUE constraint, for two people claiming one name at the same moment. */
    private fun insertAccount(username: String, passwordHash: String, now: Long) {
        try {
            accounts.insert(username, passwordHash, false, now)
        } catch (e: SQLException) {
            if (e.errorCode == SqliteConstraint) throw usernameTaken() else throw e
        }
    }

    private fun usernameTaken() =
        ApiException(HttpStatusCode.Conflict, ApiErrorCodes.UsernameTaken, "That username is taken.")

    private fun validate(username: String, password: String) {
        if (!UsernameRegex.matches(username)) {
            throw ApiException(
                HttpStatusCode.BadRequest,
                ApiErrorCodes.InvalidUsername,
                "A username is 3 to 32 lower-case letters, digits, '.', '_' or '-'.",
            )
        }
        validatePassword(password)
    }

    private fun validatePassword(password: String) {
        if (password.length !in AccountRules.MinPasswordLength..AccountRules.MaxPasswordLength) {
            throw ApiException(
                HttpStatusCode.BadRequest,
                ApiErrorCodes.InvalidPassword,
                "A password is at least ${AccountRules.MinPasswordLength} characters.",
            )
        }
    }

    private companion object {
        val UsernameRegex = Regex(AccountRules.UsernamePattern)
        val InviteLifetime = 7.days

        /** SQLITE_CONSTRAINT: the only constraint an account insert can break is the unique username. */
        const val SqliteConstraint = 19
    }
}
