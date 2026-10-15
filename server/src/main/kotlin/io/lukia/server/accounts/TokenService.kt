package io.lukia.server.accounts

import io.ktor.http.HttpStatusCode
import io.lukia.api.ApiErrorCodes
import io.lukia.api.TokenResponse
import io.lukia.server.db.ServerDatabase
import io.lukia.server.http.ApiException
import java.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/** Who a valid access token belongs to, and which sign-in issued it. */
data class SignedInAccount(
    val id: Long,
    val username: String,
    val isAdmin: Boolean,
    val familyId: Long,
)

/**
 * Issues, checks, rotates and revokes tokens - see
 * docs/decisions/0005-password-accounts.md for why they are opaque and how a
 * reused refresh token is treated.
 */
class TokenService(
    private val database: ServerDatabase,
    private val secrets: SecretTokens,
    private val clock: Clock,
) {
    private val queries get() = database.tokenQueries

    /** Starts a new sign-in for [accountId]. */
    fun issue(accountId: Long): TokenResponse = database.transactionWithResult {
        val now = clock.millis()
        sweep(now)

        queries.insertFamily(accountId, now)
        issueInFamily(database.accountQueries.lastInsertId().executeAsOne(), now)
    }

    fun accountFor(accessToken: String): SignedInAccount? =
        queries.selectAccess(secrets.hash(accessToken), clock.millis()).executeAsOneOrNull()?.let {
            SignedInAccount(id = it.id, username = it.username, isAdmin = it.isAdmin, familyId = it.familyId)
        }

    /**
     * Swaps a refresh token for a new pair. A token already swapped once is
     * somebody else's copy racing its owner, so the whole sign-in goes.
     */
    fun refresh(refreshToken: String): TokenResponse {
        val outcome = database.transactionWithResult {
            val now = clock.millis()
            sweep(now)
            val hash = secrets.hash(refreshToken)
            val stored = queries.selectRefresh(hash).executeAsOneOrNull()

            when {
                stored == null || stored.expiresAt <= now -> null
                stored.usedAt != null -> {
                    queries.deleteFamily(stored.familyId)
                    null
                }
                else -> {
                    queries.markRefreshUsed(now, hash)
                    issueInFamily(stored.familyId, now)
                }
            }
        }

        // Thrown outside the transaction, so that revoking a reused token's family is kept.
        return outcome ?: throw ApiException(
            HttpStatusCode.Unauthorized,
            ApiErrorCodes.Unauthorized,
            "This session has ended. Sign in again.",
        )
    }

    fun revoke(familyId: Long) {
        queries.deleteFamily(familyId)
    }

    fun revokeAll(accountId: Long) {
        queries.deleteFamiliesOfAccount(accountId)
    }

    private fun sweep(now: Long) {
        queries.deleteExpiredFamilies(now)
        queries.deleteExpiredAccess(now)
        queries.deleteExpiredRefresh(now)
    }

    private fun issueInFamily(familyId: Long, now: Long): TokenResponse {
        val access = secrets.generate()
        val refresh = secrets.generate()
        val accessExpiresAt = now + AccessLifetime.inWholeMilliseconds
        val refreshExpiresAt = now + RefreshLifetime.inWholeMilliseconds

        queries.insertAccess(secrets.hash(access), familyId, accessExpiresAt)
        queries.insertRefresh(secrets.hash(refresh), familyId, refreshExpiresAt)

        return TokenResponse(access, accessExpiresAt, refresh, refreshExpiresAt)
    }

    companion object {
        val AccessLifetime = 15.minutes
        val RefreshLifetime = 30.days
    }
}
