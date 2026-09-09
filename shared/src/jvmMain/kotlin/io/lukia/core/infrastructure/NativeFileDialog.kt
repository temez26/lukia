package io.lukia.core.infrastructure

import io.lukia.core.domain.document.DocumentType
import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.io.File

/**
 * The desktop's own open panel.
 *
 * [FileDialog] rather than Swing's `JFileChooser`, which this used to open. A
 * `JFileChooser` is drawn by Java: on macOS a dialog that looks nothing like the
 * Finder panel every other application opens, on Windows the old common dialog
 * rather than the Explorer one. `FileDialog` is a thin wrapper over
 * `NSOpenPanel` and the Windows platform dialog, so the reader gets the panel
 * they know - their sidebar, their recent places.
 *
 * The trade is the filter. `FilenameFilter` is honoured on macOS and Linux and
 * ignored by the Windows dialog, which reads a glob out of the file field
 * instead - so each platform is given the one it reads and neither is given the
 * other's.
 */
internal fun openDocuments(title: String): List<File> {
    val dialog = FileDialog(ownerFrame(), title, FileDialog.LOAD)

    // A reader adding books has usually just saved several, and one trip
    // through the panel should be enough for all of them.
    dialog.isMultipleMode = true
    dialog.filenameFilter = SupportedDocuments

    // Windows only, and deliberately. That dialog ignores the filter above and
    // reads a glob out of the file field instead; the platforms that honour the
    // filter would take this as a file name and prefill the panel with it.
    if (isWindows) dialog.file = SupportedDocumentPattern

    dialog.isVisible = true

    return dialog.files?.toList().orEmpty()
}

/**
 * The window the dialog belongs to.
 *
 * Without an owner the panel is not the app's: it is not app-modal, and it can
 * end up behind the window that opened it with nothing on screen explaining why
 * the app has stopped responding. Compose's own handle on its window is
 * internal, so the owner is taken from the focus manager - which, at the moment
 * a reader has just pressed a button in that window, is answering about that
 * window.
 *
 * Null is an acceptable answer and the same one the app gave before: a dialog
 * with no owner, rather than no dialog.
 */
private fun ownerFrame(): Frame? {
    val active: Window? = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow

    return active as? Frame ?: Frame.getFrames().firstOrNull { it.isShowing }
}

/**
 * A folder, on the one platform that will give one.
 *
 * Setting this property puts `NSOpenPanel` into directory mode, so a reader
 * choosing where their books live gets the Finder panel rather than a drawn
 * approximation of one. The property is read when the dialog is shown and put
 * back afterwards, since it would otherwise turn the *file* panel into a folder
 * panel the next time one opened.
 *
 * Windows and Linux have no equivalent - `FileDialog` there cannot select a
 * directory at all - and browse the app's own panel instead, so they never reach
 * this. See `PlatformFolderPicker.jvm`.
 */
internal fun openFolder(title: String): File? {
    System.setProperty(APPLE_DIRECTORY_MODE, "true")

    return try {
        val dialog = FileDialog(ownerFrame(), title, FileDialog.LOAD)
        dialog.isVisible = true

        dialog.directory?.let { directory ->
            File(directory, dialog.file.orEmpty())
        }
    } finally {
        System.setProperty(APPLE_DIRECTORY_MODE, "false")
    }
}

private const val APPLE_DIRECTORY_MODE = "apple.awt.fileDialogForDirectories"

/** Every extension the library reads - see [DocumentType.supportedExtensions]. */
private val SupportedDocuments = java.io.FilenameFilter { _, name ->
    DocumentType.fromExtension(name.substringAfterLast('.', "")) != DocumentType.UNKNOWN
}

/**
 * The same set as a Windows filter pattern. Semicolon-separated globs are what
 * that dialog reads out of the file field.
 */
private val SupportedDocumentPattern =
    DocumentType.supportedExtensions.joinToString(";") { "*.$it" }
