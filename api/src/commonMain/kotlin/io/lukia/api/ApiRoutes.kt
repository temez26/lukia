package io.lukia.api

/**
 * Where each endpoint lives, written once for both ends of the wire.
 *
 * Versioned by path: an installed app can be talking to a newer server, so a
 * change it could not read goes under a new prefix rather than replacing this one.
 */
object ApiRoutes {
    const val Prefix = "/api/v1"

    const val Health = "$Prefix/health"

    /** Creates the first account, which is the admin. Refused once any account exists. */
    const val Setup = "$Prefix/setup"

    const val SignIn = "$Prefix/auth/signin"
    const val Refresh = "$Prefix/auth/refresh"
    const val SignOut = "$Prefix/auth/signout"

    /** Creates an account with an invite code, signed in. */
    const val Redeem = "$Prefix/auth/redeem"

    /** Admin only: makes an invite code. */
    const val Invites = "$Prefix/invites"

    /** The signed-in account: GET to see it, DELETE to delete it. */
    const val Me = "$Prefix/me"

    /** Admin only: sets the password of the account named by `{username}`. */
    const val AccountPassword = "$Prefix/accounts/{username}/password"

    fun accountPassword(username: String) = AccountPassword.replace("{username}", username)

    /**
     * GET: the library's changes since `?since=` (0 for everything). POST: upload
     * a book as the raw request body, named by `?fileName=`.
     */
    const val Books = "$Prefix/books"

    /** DELETE, admin only: takes the book out of the shared library. */
    const val Book = "$Prefix/books/{id}"

    /** The book's file. Answers `Range` requests, so a broken download can resume. */
    const val BookFile = "$Prefix/books/{id}/file"

    const val BookCover = "$Prefix/books/{id}/cover"

    fun book(id: String) = Book.replace("{id}", id)

    fun bookFile(id: String) = BookFile.replace("{id}", id)

    fun bookCover(id: String) = BookCover.replace("{id}", id)

    /**
     * GET: a server-sent event stream, held open, naming each kind of change the
     * signed-in account should catch up on as it happens - `library`, `state` or
     * `sessions`. A hint to ask the endpoints below now, not the change itself.
     */
    const val MyEvents = "$Prefix/me/events"

    /** GET: changes to the signed-in account's book state since `?since=`. */
    const val MyBooks = "$Prefix/me/books"

    /** PUT a `BookStateRequest`; answers with the state as it stands after it. */
    const val MyBook = "$Prefix/me/books/{id}"

    /** GET: the signed-in account's sessions changed since `?since=`. */
    const val MySessions = "$Prefix/me/sessions"

    /** PUT a `SessionRequest` under the session's own UUID. */
    const val MySession = "$Prefix/me/sessions/{id}"

    fun myBook(id: String) = MyBook.replace("{id}", id)

    fun mySession(id: String) = MySession.replace("{id}", id)
}
