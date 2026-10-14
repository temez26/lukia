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
}
