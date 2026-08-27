package io.lukia.features.library.presentation.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.lukia.core.domain.PickedDocument
import io.lukia.core.domain.PickedFolder
import java.io.File

/** What the reader is being asked for, and what to do with the answer. */
internal sealed interface DesktopPick {

    /** True where only folders may be chosen, which is also what the list shows. */
    val foldersOnly: Boolean

    data class Books(val onPicked: (List<PickedDocument>) -> Unit) : DesktopPick {
        override val foldersOnly = false
    }

    data class Folder(val onPicked: (PickedFolder?) -> Unit) : DesktopPick {
        override val foldersOnly = true
    }
}

/**
 * The one browsing panel the desktop app has, and what it is currently being
 * asked for.
 *
 * Held here rather than beside the button that opened it, because the panel is
 * drawn once at the root of the app - see `PlatformPickerHost` - and the two
 * sides have to be able to find each other. It is process-scoped for the same
 * reason the window is: there is one app, one window, and at most one question
 * being put to the reader in it.
 *
 * A second request while one is open replaces it rather than queueing behind it.
 * There is no way to make one - every control that opens this is behind the
 * panel while it is up - and answering a question nobody can still remember
 * asking would be worse than dropping it.
 */
internal object DesktopPicks {

    var pending by mutableStateOf<DesktopPick?>(null)
        private set

    /**
     * Where the browser last got to, so that adding a second book from the same
     * folder is not the same walk down from the home folder again.
     *
     * Not stored between runs. Where a reader keeps books is a fact worth
     * remembering, but it is one the library itself already holds - and an app
     * that opened on the folder somebody was last poking around in would be
     * remembering the wrong thing.
     */
    private var visited: File? = null

    fun ask(pick: DesktopPick) {
        pending = pick
    }

    fun lastFolder(): File = visited ?: File(System.getProperty("user.home").orEmpty())

    fun remember(folder: File) {
        visited = folder
    }

    /** Nothing chosen, which every caller already handles: a cancelled picker. */
    fun cancel() {
        val asked = pending ?: return
        pending = null

        when (asked) {
            is DesktopPick.Books -> asked.onPicked(emptyList())
            is DesktopPick.Folder -> asked.onPicked(null)
        }
    }

    fun answer(paths: List<String>) {
        val asked = pending ?: return
        pending = null

        when (asked) {
            is DesktopPick.Books -> asked.onPicked(paths.map { File(it).asPickedDocument() })
            is DesktopPick.Folder -> asked.onPicked(paths.firstOrNull()?.let { File(it).asPickedFolder() })
        }
    }
}

private fun File.asPickedDocument() = PickedDocument(
    fileName = name,
    extension = extension,
    location = absolutePath,
)

private fun File.asPickedFolder() = PickedFolder(name = name, location = absolutePath)
