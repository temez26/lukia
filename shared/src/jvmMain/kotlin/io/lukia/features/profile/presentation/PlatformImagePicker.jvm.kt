package io.lukia.features.profile.presentation

import androidx.compose.runtime.Composable
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Swing's chooser, filtered to the formats every desktop JVM can decode.
 *
 * The same list the platform's own image reader registers, and no wider: a file
 * that will not decode is stored as no picture at all, and it is kinder to leave
 * it out of the dialog than to accept it and then quietly do nothing.
 */
@Composable
actual fun rememberPlatformImagePicker(): PlatformImagePickerState =
    object : PlatformImagePickerState {

        override fun launch(onPicked: (ByteArray?) -> Unit) {
            val chooser = JFileChooser()

            chooser.isMultiSelectionEnabled = false
            chooser.fileFilter = FileNameExtensionFilter(
                "Pictures",
                "png",
                "jpg",
                "jpeg",
                "gif",
                "bmp",
                "webp",
            )

            if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
                onPicked(null)
                return
            }

            // A file that cannot be read is the same answer as no file: the
            // reader keeps the picture they had, and nothing is lost.
            onPicked(runCatching { chooser.selectedFile.readBytes() }.getOrNull())
        }
    }
