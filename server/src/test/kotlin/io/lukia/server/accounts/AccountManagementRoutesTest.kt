package io.lukia.server.accounts

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.lukia.api.AccountResponse
import io.lukia.api.ApiErrorCodes
import io.lukia.api.ApiRoutes
import io.lukia.api.Credentials
import io.lukia.api.InviteResponse
import io.lukia.api.NewPasswordRequest
import io.lukia.api.RedeemInviteRequest
import io.lukia.api.TokenResponse
import io.lukia.server.AdminPassword
import io.lukia.server.TestClock
import io.lukia.server.postEmpty
import io.lukia.server.postJson
import io.lukia.server.serverTest
import io.lukia.server.setUpAdmin
import io.lukia.server.signUp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days

class AccountManagementRoutesTest {

    @Test
    fun `an invite makes one ordinary account`() = serverTest { client ->
        val admin = client.setUpAdmin()
        val invite: InviteResponse = client.postEmpty(ApiRoutes.Invites, admin.accessToken).body()

        val first = client.postJson(ApiRoutes.Redeem, RedeemInviteRequest(invite.code, "alice", AdminPassword))
        val second = client.postJson(ApiRoutes.Redeem, RedeemInviteRequest(invite.code, "bob", AdminPassword))

        assertEquals(HttpStatusCode.Created, first.status)
        assertEquals(AccountResponse("alice", isAdmin = false), client.me(first.body()).body())
        assertEquals(ApiErrorCodes.InvalidInvite, second.errorCode())
    }

    @Test
    fun `an invite expires after a week`() {
        val clock = TestClock()

        serverTest(clock = clock) { client ->
            val admin = client.setUpAdmin()
            val invite: InviteResponse = client.postEmpty(ApiRoutes.Invites, admin.accessToken).body()
            clock.advance(8.days)

            val response = client.postJson(ApiRoutes.Redeem, RedeemInviteRequest(invite.code, "alice", AdminPassword))

            assertEquals(ApiErrorCodes.InvalidInvite, response.errorCode())
        }
    }

    @Test
    fun `a taken username leaves the invite usable`() = serverTest { client ->
        val admin = client.setUpAdmin()
        val invite: InviteResponse = client.postEmpty(ApiRoutes.Invites, admin.accessToken).body()

        val taken = client.postJson(ApiRoutes.Redeem, RedeemInviteRequest(invite.code, "admin", AdminPassword))
        val retried = client.postJson(ApiRoutes.Redeem, RedeemInviteRequest(invite.code, "alice", AdminPassword))

        assertEquals(HttpStatusCode.Conflict, taken.status)
        assertEquals(ApiErrorCodes.UsernameTaken, taken.errorCode())
        assertEquals(HttpStatusCode.Created, retried.status)
    }

    @Test
    fun `only an admin can invite`() = serverTest { client ->
        val alice = client.signUp(client.setUpAdmin(), "alice")

        val response = client.postEmpty(ApiRoutes.Invites, alice.accessToken)

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `each token answers for its own account only`() = serverTest { client ->
        val admin = client.setUpAdmin()
        val alice = client.signUp(admin, "alice")
        val bob = client.signUp(admin, "bob")

        assertEquals("alice", client.me(alice).body<AccountResponse>().username)
        assertEquals("bob", client.me(bob).body<AccountResponse>().username)

        client.postEmpty(ApiRoutes.SignOut, alice.accessToken)

        assertEquals(HttpStatusCode.OK, client.me(bob).status)
    }

    @Test
    fun `deleting an account signs it out and frees nobody else's`() = serverTest { client ->
        val admin = client.setUpAdmin()
        val alice = client.signUp(admin, "alice")
        val bob = client.signUp(admin, "bob")

        val deleted = client.delete(ApiRoutes.Me) { bearerAuth(alice.accessToken) }

        assertEquals(HttpStatusCode.NoContent, deleted.status)
        assertEquals(HttpStatusCode.Unauthorized, client.me(alice).status)
        assertEquals(
            ApiErrorCodes.InvalidCredentials,
            client.postJson(ApiRoutes.SignIn, Credentials("alice", AdminPassword)).errorCode(),
        )
        assertEquals(HttpStatusCode.OK, client.me(bob).status)
    }

    @Test
    fun `the only admin cannot delete their account`() = serverTest { client ->
        val admin = client.setUpAdmin()

        val response = client.delete(ApiRoutes.Me) { bearerAuth(admin.accessToken) }

        assertEquals(ApiErrorCodes.LastAdmin, response.errorCode())
        assertEquals(HttpStatusCode.OK, client.me(admin).status)
    }

    @Test
    fun `an admin's password reset signs the account out everywhere`() = serverTest { client ->
        val admin = client.setUpAdmin()
        val alice = client.signUp(admin, "alice")

        val reset = client.resetPassword(admin, "alice", "a brand new password")

        assertEquals(HttpStatusCode.NoContent, reset.status)
        assertEquals(HttpStatusCode.Unauthorized, client.me(alice).status)
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.postJson(ApiRoutes.SignIn, Credentials("alice", AdminPassword)).status,
        )
        assertEquals(
            HttpStatusCode.OK,
            client.postJson(ApiRoutes.SignIn, Credentials("alice", "a brand new password")).status,
        )
    }

    @Test
    fun `only an admin resets passwords, and only of accounts that exist`() = serverTest { client ->
        val admin = client.setUpAdmin()
        val alice = client.signUp(admin, "alice")

        assertEquals(HttpStatusCode.Forbidden, client.resetPassword(alice, "admin", "a brand new password").status)
        assertEquals(HttpStatusCode.NotFound, client.resetPassword(admin, "nobody", "a brand new password").status)
    }

    private suspend fun HttpClient.resetPassword(by: TokenResponse, username: String, password: String) =
        put(ApiRoutes.accountPassword(username)) {
            bearerAuth(by.accessToken)
            contentType(ContentType.Application.Json)
            setBody(NewPasswordRequest(password))
        }
}
