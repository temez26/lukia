package io.lukia.features.import.presentation

import androidx.compose.runtime.Composable
import io.lukia.core.domain.PickedDocument
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun rememberPlatformFilePicker(): PlatformFilePickerState {

    return object : PlatformFilePickerState {

        override fun launch(
            onPicked: (List<PickedDocument>) -> Unit
        ) {

            val chooser = JFileChooser()

            chooser.isMultiSelectionEnabled = true

            chooser.fileFilter =
                FileNameExtensionFilter(
                    "Documents",
                    "pdf",
                    "epub",
                    "docx",
                    "txt",
                    "text",
                    "md",
                    "markdown"
                )

            val result = chooser.showOpenDialog(null)

            if (result != JFileChooser.APPROVE_OPTION) {
                onPicked(emptyList())
                return
            }

            onPicked(
                chooser.selectedFiles.map { file ->
                    PickedDocument(
                        fileName = file.name,
                        extension = file.extension,
                        location = file.absolutePath
                    )
                }
            )
        }
    }
}
