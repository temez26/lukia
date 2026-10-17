package io.lukia.features.account.domain

/**
 * The sync server's account endpoints. Every call either answers or throws an
 * [AccountFailure] saying what went wrong.
 *
 * [address] is always a checked base address - see `serverAddress`.
 */
interface ServerApi {

    suspend fun health(address: String): ServerInfo

    /** Creates the server's first account, which is its admin. */
    suspend fun setUp(address: String, username: String, password: String): AuthTokens

    suspend fun signIn(address: String, username: String, password: String): AuthTokens

    /** Creates an ordinary account, while the admin has new accounts open. */
    suspend fun signUp(address: String, username: String, password: String): AuthTokens

    /** Creates an account with an invite code. */
    suspend fun redeem(address: String, code: String, username: String, password: String): AuthTokens

    suspend fun refresh(address: String, refreshToken: String): AuthTokens

    /** Ends this sign-in on the server. */
    suspend fun signOut(address: String, accessToken: String)

    /** Deletes the account the token belongs to, with its state and sessions; the shared library keeps its books. */
    suspend fun deleteAccount(address: String, accessToken: String)

    /** Admin only: whether anyone may create an account on the server. */
    suspend fun signUpOpen(address: String, accessToken: String): Boolean

    /** Admin only: opens or closes the server to new accounts; answers with how it stands after. */
    suspend fun setSignUpOpen(address: String, accessToken: String, open: Boolean): Boolean

    /** Admin only: everyone with an account on the server, with what each has read. */
    suspend fun people(address: String, accessToken: String): List<Person>

    /** Admin only: gives [username] a new password and signs them out everywhere. */
    suspend fun resetPassword(address: String, accessToken: String, username: String, password: String)

    /** Admin only: removes [username]'s account as if they had deleted it; the books they added stay. */
    suspend fun removeAccount(address: String, accessToken: String, username: String)

    /** Who the token belongs to: the username and whether they are the admin. */
    suspend fun me(address: String, accessToken: String): Pair<String, Boolean>
}

/** A sign-in as it is kept on the device: the account in settings, the tokens in the secret store. */
data class StoredAccount(
    val account: ServerAccount,
    /** Null once the server has refused them - see [AccountStatus.NeedsSignIn]. */
    val tokens: AuthTokens?,
)

interface AccountStore {

    fun load(): StoredAccount?

    fun save(account: ServerAccount, tokens: AuthTokens)

    /** New tokens for the account already stored, after a refresh. */
    fun saveTokens(tokens: AuthTokens)

    /** Forgets the tokens and keeps the account, so signing in again needs only the password. */
    fun forgetTokens()

    /** Forgets everything: signed out. */
    fun clear()

    /**
     * The server and username of the last sign-in, kept through signing out so
     * signing in again asks only for the password. Null before the first.
     */
    fun lastSignIn(): LastSignIn?
}

/** Where, and as whom, this device last signed in. */
data class LastSignIn(val address: String, val username: String)
