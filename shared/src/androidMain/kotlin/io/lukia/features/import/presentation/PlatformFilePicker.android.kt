package io.lukia.features.import.presentation

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import io.lukia.core.domain.PickedDocument

/**
 * What the system picker will let through.
 *
 * Markdown is claimed both ways on purpose: providers disagree about what a
 * `.md` file is, and one that reports it as plain text would otherwise be greyed
 * out in the picker for a format this app reads perfectly well.
 */
private val SUPPORTED_MIME_TYPES = arrayOf(
    "application/pdf",
    "application/epub+zip",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "text/plain",
    "text/markdown"
)

@Composable
actual fun rememberPlatformFilePicker(): PlatformFilePickerState {

    val context = LocalContext.current

    val callback = remember {
        mutableStateOf<(PickedDocument?) -> Unit>({})
    }

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri == null) {
                callback.value(null)
                return@rememberLauncherForActivityResult
            }

            val resolver = context.contentResolver

            var fileName = "Unknown"

            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->

                if (cursor.moveToFirst()) {

                    val index =
                        cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)

                    if (index >= 0) {
                        fileName = cursor.getString(index)
                    }
                }
            }

            val extension =
                fileName.substringAfterLast('.', "")

            callback.value(
                PickedDocument(
                    fileName = fileName,
                    extension = extension,
                    location = uri.toString()
                )
            )
        }

    return object : PlatformFilePickerState {

        override fun launch(
            onPicked: (PickedDocument?) -> Unit
        ) {

            callback.value = onPicked

            launcher.launch(SUPPORTED_MIME_TYPES)
        }
    }
}