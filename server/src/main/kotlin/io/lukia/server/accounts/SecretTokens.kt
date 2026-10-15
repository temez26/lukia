package io.lukia.server.accounts

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Bearer secrets - tokens and invite codes - and the one-way form they are stored in. */
class SecretTokens(private val random: SecureRandom = SecureRandom()) {

    /** 256 random bits, URL-safe so a code survives being pasted into a link or a chat. */
    fun generate(): String {
        val bytes = ByteArray(TokenBytes).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * SHA-256, unsalted and fast on purpose: unlike a password, 256 random bits
     * leave nothing to guess, so a slow hash would only slow every request.
     */
    fun hash(token: String): String = MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val TokenBytes = 32
    }
}
