package io.lukia.core.domain

/** Secrets in memory, standing in for whatever each platform actually offers. */
class FakeSecretStore : SecretStore {

    private val secrets = mutableMapOf<String, String>()

    override fun getSecret(key: String): String? = secrets[key]

    override fun putSecret(key: String, value: String) {
        if (value.isBlank()) return removeSecret(key)

        secrets[key] = value
    }

    override fun removeSecret(key: String) {
        secrets.remove(key)
    }
}
