package io.lukia.features.import.presentation

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile
import io.lukia.core.domain.PickedFolder

@Composable
actual fun rememberPlatformFolderPicker(): PlatformFolderPickerState {

    val context = LocalContext.current

    val callback = remember {
        mutableStateOf<(PickedFolder?) -> Unit>({})
    }

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { treeUri ->

            if (treeUri == null) {
                callback.value(null)
                return@rememberLauncherForActivityResult
            }

            // Without the persistable grant the tree URI is only readable until
            // the process dies, so a folder picked today could not be rescanned
            // tomorrow. Some providers refuse to persist, hence runCatching.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            val name = DocumentFile.fromTreeUri(context, treeUri)?.name
                ?: "Folder"

            callback.value(
                PickedFolder(
                    name = name,
                    location = treeUri.toString()
                )
            )
        }

    return object : PlatformFolderPickerState {

        override fun launch(
            onPicked: (PickedFolder?) -> Unit
        ) {

            callback.value = onPicked

            launcher.launch(null)
        }
    }
}
