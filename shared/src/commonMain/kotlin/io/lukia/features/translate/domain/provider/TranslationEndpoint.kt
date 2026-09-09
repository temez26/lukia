package io.lukia.features.translate.domain.provider

/**
 * Whether an address is one this app will send a passage to unencrypted.
 *
 * Pointing the reader at their own machine means plain HTTP: a model server on a
 * laptop has no certificate, and telling somebody to obtain one before they can
 * translate a sentence is not a real answer. So cleartext is allowed to their
 * own machine or network, never across the internet - what travels here is a
 * sentence out of a book somebody is reading and that book's title, which over a
 * LAN is between them and their own computer and over the open internet is
 * between them and everyone on the way.
 *
 * This is the whole restriction on both platforms, and has to be. Android's
 * network security configuration matches on domains and cannot say "private
 * ranges only"; iOS stopped applying transport security to numeric addresses in
 * iOS 10, so `http://8.8.8.8` is a request the system is happy to make. Neither
 * platform refuses this on the app's behalf.
 *
 * Everything it cannot decide, it refuses: a name resolving to a private
 * address, an address in an unrecognised form. Each is a translation that does
 * not happen rather than a passage sent in the clear, and https is always
 * available to whoever meant it - this only ever refuses plain HTTP to a public
 * address, the one case that is a mistake rather than a choice.
 */
fun isAllowedTranslationEndpoint(url: String): Boolean {
    val trimmed = url.trim()

    if (trimmed.startsWith(HTTPS_SCHEME, ignoreCase = true)) return true
    if (!trimmed.startsWith(HTTP_SCHEME, ignoreCase = true)) return false

    return trimmed.drop(HTTP_SCHEME.length).hostOf().isPrivateAddress()
}

private const val HTTP_SCHEME = "http://"
private const val HTTPS_SCHEME = "https://"

/**
 * The host part of what is left of a URL once its scheme has been taken off.
 *
 * The userinfo is cut at the last `@` rather than the first, which is what RFC
 * 3986 says and is the difference between reading `http://a@b@evil.com` as
 * `evil.com` and reading it as something else entirely.
 */
private fun String.hostOf(): String {
    val authority = substringBefore('/').substringAfterLast('@')

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
 *
 * Carrier-grade NAT (100.64/10) is deliberately not here. It looks private and
 * is not the reader's: it is the address space an internet provider puts
 * between its customers, so a passage sent there in the clear crosses somebody
 * else's network to reach a stranger's machine.
 */
private fun String.isPrivateAddress(): Boolean {
    val host = lowercase()

    if (host == "localhost" || host.endsWith(".local")) return true

    return if (':' in host) host.isPrivateIpv6() else host.isPrivateIpv4()
}

/**
 * The IPv6 forms that mean the same machine or the same network.
 *
 * Written against the text rather than against a parsed address because the one
 * thing this must never do is guess: an abbreviation this does not recognise is
 * refused, and a reader whose server has an address in a shape not listed here
 * reaches it over https or by name.
 */
private fun String.isPrivateIpv6(): Boolean {
    val address = substringBefore('%')

    // Loopback, in the abbreviated form and in the one written out in full.
    if (address == "::1" || address == "0:0:0:0:0:0:0:1") return true

    // An IPv4 address carried inside an IPv6 one is still that IPv4 address,
    // and `::ffff:127.0.0.1` reaches exactly what `127.0.0.1` reaches.
    if (address.startsWith("::ffff:")) return address.removePrefix("::ffff:").isPrivateIpv4()

    // Unique local addresses, which are IPv6's answer to 10/8 and 192.168/16,
    // and link-local, which is what two machines settle on with no router.
    return address.startsWith("fd") || address.startsWith("fc") || address.startsWith("fe80:")
}

private fun String.isPrivateIpv4(): Boolean {
    val numbers = split(".").map { it.toIntOrNull() ?: return false }

    if (numbers.size != 4 || numbers.any { it !in 0..255 }) return false

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
