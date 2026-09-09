package io.lukia.core.infrastructure

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JvmSecretStoreTest {

    private val directory = File(
        System.getProperty("java.io.tmpdir"),
        "lukia-secrets-${kotlin.random.Random.nextInt(until = Int.MAX_VALUE)}",
    )
    private val file = File(directory, "secrets.properties")

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun aSecretIsThereTheNextTimeTheAppOpens() {
        JvmSecretStore(file).putSecret("translation.apiKey.Anthropic", "sk-ant-test")

        assertEquals("sk-ant-test", JvmSecretStore(file).getSecret("translation.apiKey.Anthropic"))
    }

    @Test
    fun anUnknownKeyHasNoSecret() {
        assertNull(JvmSecretStore(file).getSecret("nothing.here"))
    }

    @Test
    fun aRemovedSecretDoesNotComeBack() {
        val store = JvmSecretStore(file)

        store.putSecret("key", "value")
        store.removeSecret("key")

        assertNull(JvmSecretStore(file).getSecret("key"))
    }

    /** Clearing a field is how somebody undoes it, not a secret of no characters. */
    @Test
    fun storingNothingRemovesTheSecret() {
        val store = JvmSecretStore(file)

        store.putSecret("key", "value")
        store.putSecret("key", "   ")

        assertNull(JvmSecretStore(file).getSecret("key"))
    }

    /**
     * The whole point of a separate file: another account on the same machine,
     * or a backup that sweeps up the app's directory, should not come away with
     * somebody's API key.
     *
     * Asserted where the permission bits exist, which is macOS and Linux.
     * Windows has none to read: what keeps the file to its owner there is the
     * ACL inherited from the profile directory, which this code neither sets nor
     * can portably ask about, so the test says nothing rather than asserting
     * something that is not its doing.
     */
    @Test
    fun theFileIsReadableOnlyByItsOwner() {
        JvmSecretStore(file).putSecret("key", "value")

        val posix = Files.getFileAttributeView(file.toPath(), PosixFileAttributeView::class.java)
            ?: return

        assertEquals(
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            posix.readAttributes().permissions(),
        )
    }

    /** A secret must never be written into the ordinary settings file. */
    @Test
    fun secretsLiveApartFromSettings() {
        JvmSecretStore(file).putSecret("key", "value")

        val settings = File(directory, "settings.properties")

        assertTrue(!settings.exists() || !settings.readText().contains("value"))
    }
}
