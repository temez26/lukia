package io.lukia.server.accounts

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

interface PasswordHasher {
    fun hash(password: String): String

    /** False for a hash this hasher cannot read, rather than a throw: a damaged row is a failed sign-in. */
    fun verify(password: String, encoded: String): Boolean
}

/**
 * Argon2id, written in the PHC string format (`$argon2id$v=19$m=…,t=…,p=…$salt$hash`)
 * that every other Argon2 implementation reads.
 *
 * The parameters travel in each hash, so raising the cost later leaves the
 * hashes already stored verifiable. The defaults are OWASP's first
 * recommendation: 19 MiB, two passes, one lane.
 */
class Argon2PasswordHasher(
    private val memoryKiB: Int = 19_456,
    private val iterations: Int = 2,
    private val parallelism: Int = 1,
    private val random: SecureRandom = SecureRandom(),
) : PasswordHasher {

    override fun hash(password: String): String {
        val salt = ByteArray(SaltBytes).also(random::nextBytes)
        val cost = Cost(memoryKiB, iterations, parallelism)
        val hash = derive(password, salt, cost, HashBytes)

        return listOf("", Algorithm, Version, cost.encode(), salt.encode(), hash.encode()).joinToString("$")
    }

    override fun verify(password: String, encoded: String): Boolean {
        val parsed = parse(encoded) ?: return false
        val actual = derive(password, parsed.salt, parsed.cost, parsed.hash.size)

        return MessageDigest.isEqual(actual, parsed.hash)
    }

    private fun derive(password: String, salt: ByteArray, cost: Cost, length: Int): ByteArray {
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(cost.memoryKiB)
            .withIterations(cost.iterations)
            .withParallelism(cost.parallelism)
            .withSalt(salt)
            .build()

        return ByteArray(length).also { output ->
            Argon2BytesGenerator().apply { init(parameters) }.generateBytes(password.toByteArray(), output)
        }
    }

    private fun parse(encoded: String): Parsed? = runCatching {
        val match = requireNotNull(PhcPattern.matchEntire(encoded))
        fun group(name: String) = match.groups[name]!!.value

        Parsed(
            cost = Cost(group("m").toInt(), group("t").toInt(), group("p").toInt()),
            salt = group("salt").decode(),
            hash = group("hash").decode(),
        )
    }.getOrNull()

    private data class Cost(val memoryKiB: Int, val iterations: Int, val parallelism: Int) {
        fun encode() = "m=$memoryKiB,t=$iterations,p=$parallelism"
    }

    private class Parsed(val cost: Cost, val salt: ByteArray, val hash: ByteArray)

    private fun ByteArray.encode(): String = Base64.getEncoder().withoutPadding().encodeToString(this)

    private fun String.decode(): ByteArray = Base64.getDecoder().decode(this)

    private companion object {
        const val Algorithm = "argon2id"
        const val Version = "v=19"

        /** What [hash] writes, read back: the same parts joined by the same `$`. */
        val PhcPattern = Regex(
            listOf(
                "",
                Algorithm,
                Version,
                """m=(?<m>\d+),t=(?<t>\d+),p=(?<p>\d+)""",
                "(?<salt>[A-Za-z0-9+/]+)",
                "(?<hash>[A-Za-z0-9+/]+)",
            ).joinToString(Regex.escape("$")),
        )

        const val SaltBytes = 16
        const val HashBytes = 32
    }
}
