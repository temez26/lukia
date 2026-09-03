package io.lukia.core.infrastructure.diagnostics

import android.content.Context
import io.lukia.core.domain.diagnostics.CrashStore
import java.io.File

/**
 * App-private internal storage: removed with the app, and out of reach of every
 * other app on the device and of anything that browses shared storage.
 */
class AndroidCrashStore(
    private val context: Context,
) : CrashStore {

    private val file: File
        get() = File(context.filesDir, FILE_NAME)

    override fun write(report: String) = file.writeText(report)

    override fun read(): String? = file.takeIf { it.isFile }?.readText()

    override fun discard() {
        file.delete()
    }

    private companion object {
        const val FILE_NAME = "last-crash.txt"
    }
}
