package io.lukia.features.account.domain

import io.lukia.core.domain.AppError
import io.lukia.core.domain.AppErrorException

/**
 * A sync server somebody is signed in to, and who as. See
 * docs/specs/sync-server.md.
 *
 * [address] is the server's base address as it was checked, scheme included -
 * see `serverAddress`. [serverName] is what the server calls itself, shown so a
 * reader with more than one household's server can tell which this is.
 */
data class ServerAccount(
    val address: String,
    val serverName: String,
    val username: String,
    val isAdmin: Boolean,
)

/** Whether this device is signed in to a server, and whether that sign-in still works. */
sealed interface AccountStatus {

    /** The app as it has always been: everything on the device, nothing sent anywhere. */
    data object SignedOut : AccountStatus

    data class SignedIn(val account: ServerAccount) : AccountStatus

    /**
     * Signed in once, and the server no longer accepts it - the session ran
     * out, was signed out elsewhere, or the password was reset. Remembered so
     * the reader is asked for their password rather than for everything again.
     */
    data class NeedsSignIn(val account: ServerAccount) : AccountStatus
}

/** What a server says about itself before anyone has signed in. */
data class ServerInfo(
    val name: String,
    val version: String,
    /** True until the server's first account exists; the reader is then offered to create the admin. */
    val setupRequired: Boolean,
)

/** A sign-in's tokens. Times are epoch milliseconds on the server's clock. */
data class AuthTokens(
    val accessToken: String,
    val accessExpiresAt: Long,
    val refreshToken: String,
    val refreshExpiresAt: Long,
)

/**
 * What went wrong, told apart by what the reader would do about it - which is
 * also what the account screen says.
 */
enum class AccountProblem {

    /** What was typed is not a server address at all. */
    InvalidAddress,

    /** Nothing answered at that address, or the connection broke off. */
    ServerUnreachable,

    /** Something answered, and it is not a Lukia server. */
    NotALukiaServer,

    /** Plain `http://` to an address outside the reader's own network - see `isAllowedEndpoint`. */
    InsecureAddress,

    /** The username and password do not match an account. */
    SignInRejected,

    /** Too many failed sign-ins; the server is making the reader wait. */
    TooManyAttempts,

    InvalidUsername,
    InvalidPassword,
    UsernameTaken,

    /** The invite code is unknown, used or expired. */
    InvalidInvite,

    /** Somebody else set the server up first. */
    AlreadySetUp,

    /** The sign-in is no longer accepted; the reader has to sign in again. */
    SessionExpired,

    /** The server failed in a way the app has no words for. */
    ServerFailed,
}

/**
 * Carries an [AccountProblem] through the app's error channel. Read as an
 * [AppError] by anything that does not know about accounts, and by the account
 * screen for the problem it is.
 */
class AccountFailure(val problem: AccountProblem) : AppErrorException(AppError.Unexpected())
