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
}
