package io.lukia.core.infrastructure.diagnostics

import io.lukia.core.domain.diagnostics.CrashStore
import io.lukia.core.infrastructure.lukiaAppDirectory
import java.io.File

/** Beside the app's other stored state, which is where the reader's own files are not. */
class JvmCrashStore(
    private val file: File = File(lukiaAppDirectory(), FILE_NAME),
) : CrashStore {

    override fun write(report: String) {
        file.parentFile?.mkdirs()
        file.writeText(report)
    }

    override fun read(): String? = file.takeIf { it.isFile }?.readText()

    override fun discard() {
        file.delete()
    }

    private companion object {
        const val FILE_NAME = "last-crash.txt"
    }
}
