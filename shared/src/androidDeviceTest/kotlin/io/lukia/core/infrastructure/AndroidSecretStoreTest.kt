package io.lukia.core.infrastructure

import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * The reader's translation key, held by a key that never leaves the keystore.
 *
 * On a device rather than the host, and the reason the Android floor is API 23:
 * `AndroidKeyStore` is the platform, not a library, and Robolectric's stand-in
 * would only prove that a stand-in round-trips a string.
 *
 * `JvmSecretStoreTest` and `IOSSecretStoreTest` pin the same contract against
 * the store their own platform has.
 */
class AndroidSecretStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun store() = AndroidSecretStore(context)

    @AfterTest
    fun tidy() {
        Keys.forEach { store().removeSecret(it) }
    }

    /** The whole point: a key typed once is there the next time the app opens. */
    @Test
    fun aSecretIsThereTheNextTimeTheAppOpens() {
        store().putSecret(ApiKey, "sk-ant-test")

        assertEquals("sk-ant-test", store().getSecret(ApiKey))
    }

    @Test
    fun anUnknownKeyHasNoSecret() {
        assertNull(store().getSecret("nothing.here"))
    }

    @Test
    fun aSecretIsReplacedRatherThanAddedTo() {
        store().putSecret(ApiKey, "first")
        store().putSecret(ApiKey, "second")

        assertEquals("second", store().getSecret(ApiKey))
    }

    @Test
    fun aRemovedSecretDoesNotComeBack() {
        store().putSecret(ApiKey, "sk-ant-test")
        store().removeSecret(ApiKey)

        assertNull(store().getSecret(ApiKey))
    }

    /** Clearing a field is how somebody undoes it, not a secret of no characters. */
    @Test
    fun storingNothingRemovesTheSecret() {
        store().putSecret(ApiKey, "sk-ant-test")
        store().putSecret(ApiKey, "   ")

        assertNull(store().getSecret(ApiKey))
    }

    @Test
    fun twoSecretsDoNotOverwriteEachOther() {
        store().putSecret(ApiKey, "sk-ant-test")
        store().putSecret(OtherKey, "sk-openai-test")

        assertEquals("sk-ant-test", store().getSecret(ApiKey))
        assertEquals("sk-openai-test", store().getSecret(OtherKey))
    }

    /**
     * What preferences hold is ciphertext, and the reason the key goes here
     * instead of into settings. A preferences file carried off the device - by a
     * backup, by an image of the storage - has to be bytes nobody can read.
     */
    @Test
    fun whatIsOnDiskIsNotTheSecret() {
        store().putSecret(ApiKey, "sk-ant-test")

        val stored = context
            .getSharedPreferences("io.lukia.secrets", android.content.Context.MODE_PRIVATE)
            .getString(ApiKey, null)

        assertFalse(stored.isNullOrBlank(), "nothing was stored at all")
        assertFalse(stored!!.contains("sk-ant-test"), "the key was written out in the clear")
    }

    /**
     * A secret that cannot be decrypted is gone, and there is nothing to
     * recover - a restore onto another phone arrives as ciphertext with no key
     * to open it. Reported as absent rather than as a failure, so the reader is
     * asked for the key again instead of shown an error they cannot act on.
     */
    @Test
    fun ciphertextThatCannotBeDecryptedReadsAsNoSecretAtAll() {
        context
            .getSharedPreferences("io.lukia.secrets", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString(ApiKey, "bm90IGN2aXBoZXJ0ZXh0IGF0IGFsbA==")
            .commit()

        assertNull(store().getSecret(ApiKey))
    }

    private companion object {
        const val ApiKey = "translation.apiKey.Anthropic"
        const val OtherKey = "translation.apiKey.OpenAi"

        val Keys = listOf(ApiKey, OtherKey, "nothing.here")
    }
}
