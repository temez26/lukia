package io.lukia.core.domain

/**
 * Where a secret is kept, as opposed to a preference.
 *
 * Separate from [SettingsStore] because the two want opposite things. A
 * preference should be easy to read, cheap, and survive anything - losing which
 * theme somebody chose is a shrug. A secret should be hard to read, is worth
 * paying for, and losing it is far better than leaking it: an API key that has
 * to be pasted again is an inconvenience, one sitting in a file anything on the
 * machine can open is somebody else's bill.
 *
 * Each platform backs this with whatever it actually has - the keystore on
 * Android, the keychain on iOS - rather than with a second preferences file
 * under a different name.
 *
 * Deliberately synchronous, like [SettingsStore], and for the same reason: the
 * settings screen and the translator both read this while composing or
 * mid-request, and neither has anywhere to put a wait.
 */
interface SecretStore {

    fun getSecret(key: String): String?

    /** Storing a blank value removes it: a cleared field is not a secret of no characters. */
    fun putSecret(key: String, value: String)

    fun removeSecret(key: String)
}
