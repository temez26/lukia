package io.lukia.features.import.presentation

import androidx.compose.runtime.Composable
import io.lukia.core.domain.PickedDocument
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun rememberPlatformFilePicker(): PlatformFilePickerState {

    return object : PlatformFilePickerState {

        override fun launch(
            onPicked: (PickedDocument?) -> Unit
        ) {

            val chooser = JFileChooser()

            chooser.fileFilter =
                FileNameExtensionFilter(
                    "PDF or EPUB",
                    "pdf",
                    "epub"
                )

            val result = chooser.showOpenDialog(null)

            if (result == JFileChooser.APPROVE_OPTION) {

                val file = chooser.selectedFile

                onPicked(
                    PickedDocument(
                        fileName = file.name,
                        extension = file.extension,
                        location = file.absolutePath
                    )
                )

            } else {

                onPicked(null)

            }

        }
    }
}