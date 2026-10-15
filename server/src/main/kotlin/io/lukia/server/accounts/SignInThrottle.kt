package io.lukia.server.accounts

import io.ktor.http.HttpStatusCode
import io.lukia.api.ApiErrorCodes
import io.lukia.server.http.ApiException
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Slows password guessing: after too many failed sign-ins for one username, or
 * from one address, further attempts are refused until the window has passed.
 *
 * Held in memory, so a restart forgets it; that costs an attacker one restart
 * per window, which they cannot cause. Behind a reverse proxy every request
 * comes from the proxy's address, so the address limit is set well above the
 * username one and is the backstop rather than the main defence.
 */
class SignInThrottle(
    private val clock: Clock,
    private val maxPerUsername: Int = 10,
    private val maxPerAddress: Int = 50,
    private val window: Duration = 15.minutes,
) {
    private class Window(val startedAt: Long, val failures: Int)

    private val failures = HashMap<String, Window>()

    /**
     * Counts an attempt as failed before its password is checked, so attempts
     * sent all at once cannot each slip under the limit while the slow hash
     * runs. One that succeeds is given back with [succeeded].
     */
    fun begin(username: String, address: String) = synchronized(this) {
        val now = clock.millis()
        val keys = listOf(usernameKey(username), addressKey(address))
        if (count(keys[0], now) >= maxPerUsername || count(keys[1], now) >= maxPerAddress) {
            throw ApiException(
                HttpStatusCode.TooManyRequests,
                ApiErrorCodes.TooManyAttempts,
                "Too many failed sign-ins. Try again in a few minutes.",
            )
        }

        if (failures.size > PruneAbove) failures.values.removeIf { it.isOver(now) }
        keys.forEach { key ->
            val current = failures[key]?.takeUnless { it.isOver(now) }
            failures[key] = Window(current?.startedAt ?: now, (current?.failures ?: 0) + 1)
        }
    }

    /** Clears the username, so the owner is not locked out by their own typos, and gives the address its attempt back. */
    fun succeeded(username: String, address: String) = synchronized(this) {
        failures.remove(usernameKey(username))
        val key = addressKey(address)
        failures[key]?.let { failures[key] = Window(it.startedAt, (it.failures - 1).coerceAtLeast(0)) }
    }

    private fun count(key: String, now: Long): Int = failures[key]?.takeUnless { it.isOver(now) }?.failures ?: 0

    private fun Window.isOver(now: Long) = now - startedAt >= window.inWholeMilliseconds

    private fun usernameKey(username: String) = "u:$username"

    private fun addressKey(address: String) = "a:$address"

    private companion object {
        /** Bounds memory against a flood of made-up usernames. */
        const val PruneAbove = 10_000
    }
}
