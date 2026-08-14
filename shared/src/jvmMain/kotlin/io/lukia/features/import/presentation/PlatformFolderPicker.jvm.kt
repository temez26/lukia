package io.lukia.features.import.presentation

import androidx.compose.runtime.Composable
import io.lukia.core.domain.PickedFolder
import javax.swing.JFileChooser

@Composable
actual fun rememberPlatformFolderPicker(): PlatformFolderPickerState {

    return object : PlatformFolderPickerState {

        override fun launch(
            onPicked: (PickedFolder?) -> Unit
        ) {

            val chooser = JFileChooser()

            chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            chooser.dialogTitle = "Choose a library folder"

            val result = chooser.showOpenDialog(null)

            if (result == JFileChooser.APPROVE_OPTION) {

                val directory = chooser.selectedFile

                onPicked(
                    PickedFolder(
                        name = directory.name,
                        location = directory.absolutePath
                    )
                )

            } else {

                onPicked(null)

            }

        }
    }
}
