package io.lukia.features.account.domain

import io.lukia.core.domain.network.isAllowedEndpoint

/**
 * The server address the reader typed, as the app will use it, or an
 * [AccountFailure] saying why it will not.
 *
 * A scheme is supplied when none was typed, because most people type what a
 * friend read out to them - `192.168.1.10:8080`, `books.example.com`. An address
 * on the reader's own network gets `http://`, since a server at home rarely has
 * a certificate; anything else gets `https://`, since a password must not cross
 * the internet in the clear. A scheme typed on purpose is kept, and plain HTTP
 * to a public address is refused, by the rule the translator is held to - see
 * [isAllowedEndpoint].
 */
fun serverAddress(typed: String): String {
    val address = withScheme(typed)
    if (!address.isWellFormed()) throw AccountFailure(AccountProblem.InvalidAddress)
    if (!isAllowedEndpoint(address)) throw AccountFailure(AccountProblem.InsecureAddress)
    return address
}

private fun withScheme(typed: String): String {
    // The scheme is looked for before the trailing slashes go, or `https://`
    // alone would lose its own slashes and read as a host called `https:`.
    val hasScheme = typed.contains("://")
    val trimmed = typed.trim().trimEnd('/')

    return when {
        hasScheme -> trimmed
        isAllowedEndpoint("http://$trimmed") -> "http://$trimmed"
        else -> "https://$trimmed"
    }
}

/** HTTP or HTTPS, with a host, and nothing a person typed by accident - a space in the middle. */
private fun String.isWellFormed(): Boolean {
    val isHttp = startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)
    val host = substringAfter("://", missingDelimiterValue = "").substringBefore('/')
    return isHttp && host.isNotEmpty() && none(Char::isWhitespace)
}
