package io.lukia.features.library.presentation

import androidx.compose.runtime.Composable
import io.lukia.core.domain.PickedFolder
import io.lukia.core.infrastructure.openFolder

@Composable
actual fun rememberPlatformFolderPicker(): PlatformFolderPickerState =
    object : PlatformFolderPickerState {

        override fun launch(
            onPicked: (PickedFolder?) -> Unit,
        ) {
            val directory = openFolder(title = "Choose a library folder")

            onPicked(
                directory?.let {
                    PickedFolder(name = it.name, location = it.absolutePath)
                },
            )
        }
    }
