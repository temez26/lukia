package io.lukia.core.infrastructure

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.lukia.core.domain.SecretStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Secrets encrypted by a key that never leaves the Android keystore.
 *
 * The ciphertext lives in ordinary preferences, which is fine, because the key
 * that would decrypt it does not: it is generated inside the keystore and can
 * only be used through it. On a device with hardware backing it is held in the
 * secure element, and on one without it is still outside this app's own storage.
 * Copying the preferences file off the device yields bytes nobody can read.
 *
 * Written against the platform rather than through `EncryptedSharedPreferences`
 * so that this depends on nothing beyond Android itself - and so it keeps
 * working now that library has stopped being maintained.
 */
class AndroidSecretStore(context: Context) : SecretStore {

    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun getSecret(key: String): String? {
        val stored = preferences.getString(key, null) ?: return null

        // A secret that cannot be decrypted is gone: the keystore key is
        // dropped when the screen lock changes or the app is restored onto a
        // different device, and there is nothing to recover. Reported as absent
        // rather than as a failure, so the reader is asked for it again.
        return runCatching { decrypt(stored) }.getOrNull()
    }

    override fun putSecret(key: String, value: String) {
        if (value.isBlank()) return removeSecret(key)

        runCatching { encrypt(value) }
            .onSuccess { preferences.edit().putString(key, it).apply() }
    }

    override fun removeSecret(key: String) {
        preferences.edit().remove(key).apply()
    }

    /**
     * The nonce is prepended rather than stored beside the ciphertext: it has to
     * be unique per encryption and useless on its own, so it belongs with the
     * bytes it applies to rather than in a second entry that could drift.
     */
    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(value.encodeToByteArray())

        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val nonce = bytes.copyOfRange(0, NONCE_BYTES)
        val encrypted = bytes.copyOfRange(NONCE_BYTES, bytes.size)

        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, nonce))
        }

        return cipher.doFinal(encrypted).decodeToString()
    }

    /** The app's own key in the keystore, generated the first time it is wanted. */
    private fun key(): SecretKey {
        val keystore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keystore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)

        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // Deliberately not requiring the device to be unlocked: a book
                // is read on a train with the screen on, and a translation that
                // failed until the reader authenticated again would be a worse
                // trade than the one this is making.
                .build()
        )

        return generator.generateKey()
    }

    private companion object {
        const val PREFERENCES_NAME = "io.lukia.secrets"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "io.lukia.secrets"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
    }
}
