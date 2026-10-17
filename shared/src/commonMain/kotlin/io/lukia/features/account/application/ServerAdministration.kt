package io.lukia.features.account.application

import io.lukia.features.account.domain.AccountStatus
import io.lukia.features.account.domain.Person
import io.lukia.features.account.domain.ServerApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Looking after the server the reader is signed in to as its admin.
 *
 * Kept apart from [AccountSession], which is about this device's own sign-in:
 * these act on the server and on other people, and only an admin sees them.
 * The server checks that it is an admin asking every time; nothing here is a
 * permission, only a way to ask.
 */
class ServerAdministration(
    private val session: AccountSession,
    private val api: ServerApi,
) {
    /**
     * The username this device is signed in as, while it is the server's admin -
     * the only one these are offered to - and null otherwise.
     */
    val signedInAdmin: Flow<String?> =
        session.status.map { status -> (status as? AccountStatus.SignedIn)?.account?.takeIf { it.isAdmin }?.username }

    suspend fun signUpOpen(): Boolean = session.authorized { address, token -> api.signUpOpen(address, token) }

    suspend fun setSignUpOpen(open: Boolean): Boolean =
        session.authorized { address, token -> api.setSignUpOpen(address, token, open) }

    suspend fun people(): List<Person> = session.authorized { address, token -> api.people(address, token) }

    suspend fun resetPassword(username: String, password: String) =
        session.authorized { address, token -> api.resetPassword(address, token, username, password) }

    suspend fun removeAccount(username: String) =
        session.authorized { address, token -> api.removeAccount(address, token, username) }
}
