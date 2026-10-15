package io.lukia.features.sync.application

import io.lukia.features.account.domain.AccountProblem

/**
 * Where this device stands with the server, for a reader who wants to know
 * whether what they see is what the account has. Times are epoch milliseconds
 * on this device's clock.
 */
data class SyncStatus(
    /** A round is under way. */
    val running: Boolean = false,
    /** When the last round finished cleanly, or null if none has since the app started. */
    val lastSyncedAt: Long? = null,
    /** Why the last round failed, or null if it did not. */
    val problem: AccountProblem? = null,
    /** When the next try after a failure is due, or null when none is waiting. */
    val retryAt: Long? = null,
)
