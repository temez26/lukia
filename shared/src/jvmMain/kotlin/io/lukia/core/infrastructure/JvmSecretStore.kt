package io.lukia.core.infrastructure

import io.lukia.core.domain.storage.SecretStore
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.Properties

/**
 * Secrets in a file only their owner can open.
 *
 * The weakest of the three platforms, and worth being plain about: the desktop
 * has no secret store reachable without a dependency and a process call per
 * platform - the macOS keychain, libsecret and DPAPI are three mechanisms, two
 * of which can block on a prompt. So this does the one thing genuinely
 * available: keeps secrets out of the ordinary settings file and restricts that
 * file to the account that owns it.
 *
 * That defends against another account on the same machine and a backup copying
 * the app's directory, not against something already running as this user -
 * which could read the keychain entries any other approach would create anyway.
 * It is not nothing, and it is not encryption; calling it encryption would be
 * worse than the gap itself.
 */
class JvmSecretStore(
    /** Injectable so tests can work in a temporary directory. */
    private val file: File = File(lukiaAppDirectory(), FILE_NAME),
) : SecretStore {

    private val secrets: Properties by lazy {
        Properties().apply {
            if (file.isFile) {
                runCatching { file.inputStream().use { load(it) } }
            }
        }
    }

    override fun getSecret(key: String): String? = secrets.getProperty(key)

    override fun putSecret(key: String, value: String) {
        if (value.isBlank()) return removeSecret(key)

        secrets.setProperty(key, value)
        write()
    }

    override fun removeSecret(key: String) {
        secrets.remove(key)
        write()
    }

    /**
     * The file's permissions are set before anything is written to it, so a
     * secret is never briefly on disk in a file the rest of the machine can
     * read.
     *
     * Set on every write rather than only at creation. A file that already
     * exists is not a file that is already restricted: it may have been written
     * by a build of this app from before these permissions were applied, or
     * restored from an archive that did not carry them, and a check that skips
     * it would leave the one case that most needs fixing exactly as it was.
     */
    private fun write() {
        runCatching {
            file.parentFile?.mkdirs()

            if (!file.exists()) file.createNewFile()

            file.restrictToOwner()

            file.outputStream().use { secrets.store(it, COMMENT) }
        }
    }

    private fun File.restrictToOwner() {
        // POSIX where there is one, which is macOS and Linux.
        val posix = runCatching {
            Files.setPosixFilePermissions(
                toPath(),
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        }

        if (posix.isSuccess) return

        // Windows, where the same intent is three separate flags and the
        // `ownerOnly` argument is what actually does the work.
        setReadable(false, false)
        setWritable(false, false)
        setReadable(true, true)
        setWritable(true, true)
    }

    private companion object {
        const val FILE_NAME = "secrets.properties"
        const val COMMENT = "Lukia secrets - keep this file to yourself"
    }
}
