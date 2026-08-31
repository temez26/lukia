package io.lukia.features.translate.domain.provider

/**
 * Whether an address is one this app will send a passage to unencrypted.
 *
 * Pointing the reader at their own machine means plain HTTP: a model server on
 * a laptop has no certificate, and telling somebody to obtain one before they
 * can translate a sentence is not a real answer. So cleartext is allowed - but
 * only to their own machine or their own network, never across the internet.
 *
 * The distinction matters because what travels here is a sentence out of a book
 * somebody is reading, along with the title of that book. Over a LAN that is
 * between them and their own computer. Over the open internet in plain text it
 * is between them and everyone on the way.
 *
 * HTTPS is accepted anywhere. This only ever refuses plain HTTP to a public
 * address, which is the one case that is a mistake rather than a choice.
 */
fun isAllowedTranslationEndpoint(url: String): Boolean {
    val trimmed = url.trim()

    if (trimmed.startsWith("https://", ignoreCase = true)) return true
    if (!trimmed.startsWith("http://", ignoreCase = true)) return false

    return trimmed.removePrefix("http://").removePrefix("HTTP://").hostOf().isPrivateAddress()
}

/** The host part of what is left of a URL once its scheme has been taken off. */
private fun String.hostOf(): String {
    val authority = substringBefore('/').substringAfter('@')

    // An IPv6 literal is bracketed, and its own colons are not a port separator.
    if (authority.startsWith("[")) return authority.substringAfter('[').substringBefore(']')

    return authority.substringBefore(':')
}

/**
 * Whether a host is this machine or something on the same network.
 *
 * The private ranges are the ones set aside for exactly this - a home or office
 * network - plus loopback, link-local, and the address an Android emulator
 * reaches its host on. A name is trusted only when it is one of the local ones;
 * anything else resolves somewhere this code cannot see.
 */
private fun String.isPrivateAddress(): Boolean {
    val host = lowercase()

    if (host == "localhost" || host == "::1" || host.endsWith(".local")) return true

    val octets = host.split(".")
    if (octets.size != 4) return false

    val numbers = octets.map { it.toIntOrNull() ?: return false }
    if (numbers.any { it !in 0..255 }) return false

    val (first, second) = numbers

    return when (first) {
        // Loopback, and the whole of it: 127.0.0.1 is the common one but the
        // range is a range.
        127 -> true
        10 -> true
        // 172.16.0.0 - 172.31.255.255, which is not all of 172.
        172 -> second in 16..31
        192 -> second == 168
        // Link-local, which is what two machines settle on with no router.
        169 -> second == 254
        else -> false
    }
}
