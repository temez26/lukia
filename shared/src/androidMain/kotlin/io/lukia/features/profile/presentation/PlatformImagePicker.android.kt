package io.lukia.features.profile.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The photo picker, rather than the document picker the import uses.
 *
 * It is the one the system offers for exactly this - it shows the gallery, it
 * needs no storage permission at all, and on devices without it the contract
 * falls back to the document picker on its own. A reader choosing a picture of
 * themselves is looking in their photographs, not in Downloads.
 */
@Composable
internal actual fun rememberPlatformImagePicker(): PlatformImagePickerState {
    val context = LocalContext.current

    val callback = remember { mutableStateOf<(ByteArray?) -> Unit>({}) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        callback.value(
            uri?.let {
                // The grant lasts as long as this activity, which is all it has
                // to: the bytes are read here and kept by the app, and nothing
                // ever opens the reader's own file again.
                runCatching {
                    context.contentResolver.openInputStream(it)?.use { stream ->
                        stream.readBytes()
                    }
                }.getOrNull()
            },
        )
    }

    return remember(launcher) {
        object : PlatformImagePickerState {

            override fun launch(onPicked: (ByteArray?) -> Unit) {
                callback.value = onPicked

                launcher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }
        }
    }
}
