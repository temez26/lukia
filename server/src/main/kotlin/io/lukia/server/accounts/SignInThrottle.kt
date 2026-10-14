package io.lukia.server.accounts

import io.ktor.http.HttpStatusCode
import io.lukia.api.ApiErrorCodes
import io.lukia.server.http.ApiException
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
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

    private val failures = ConcurrentHashMap<String, Window>()

    fun check(username: String, address: String) {
        if (count(usernameKey(username)) >= maxPerUsername || count(addressKey(address)) >= maxPerAddress) {
            throw ApiException(
                HttpStatusCode.TooManyRequests,
                ApiErrorCodes.TooManyAttempts,
                "Too many failed sign-ins. Try again in a few minutes.",
            )
        }
    }

    fun recordFailure(username: String, address: String) {
        val now = clock.millis()
        if (failures.size > PruneAbove) failures.values.removeIf { it.isOver(now) }

        listOf(usernameKey(username), addressKey(address)).forEach { key ->
            failures.compute(key) { _, current ->
                when {
                    current == null || current.isOver(now) -> Window(now, 1)
                    else -> Window(current.startedAt, current.failures + 1)
                }
            }
        }
    }

    /** A successful sign-in clears its username, so the owner is not locked out by their own typos. */
    fun clear(username: String) {
        failures.remove(usernameKey(username))
    }

    private fun count(key: String): Int = failures[key]?.takeUnless { it.isOver(clock.millis()) }?.failures ?: 0

    private fun Window.isOver(now: Long) = now - startedAt >= window.inWholeMilliseconds

    private fun usernameKey(username: String) = "u:$username"

    private fun addressKey(address: String) = "a:$address"

    private companion object {
        /** Bounds memory against a flood of made-up usernames. */
        const val PruneAbove = 10_000
    }
}
