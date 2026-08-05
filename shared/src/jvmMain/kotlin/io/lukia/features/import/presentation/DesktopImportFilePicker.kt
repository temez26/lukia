package io.lukia.features.import.presentation

import io.lukia.features.import.domain.PickedDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

class DesktopImportFilePicker : ImportFilePicker {
    override suspend fun pick(): PickedDocument? = withContext(Dispatchers.IO) {
        val fileChooser = JFileChooser()
        val filter = FileNameExtensionFilter("PDF or EPUB files", "pdf", "epub")
        fileChooser.fileFilter = filter

        val result = fileChooser.showOpenDialog(null)
        if (result == JFileChooser.APPROVE_OPTION) {
            val file = fileChooser.selectedFile
            PickedDocument(
                fileName = file.name,
                extension = file.extension,
                absolutePath = file.absolutePath
            )
        } else {
            null
        }
    }
}
