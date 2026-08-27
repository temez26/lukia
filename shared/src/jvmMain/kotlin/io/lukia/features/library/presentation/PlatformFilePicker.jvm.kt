package io.lukia.features.library.presentation

import androidx.compose.runtime.Composable
import io.lukia.core.domain.PickedDocument
import io.lukia.core.infrastructure.openDocuments

@Composable
actual fun rememberPlatformFilePicker(): PlatformFilePickerState =
    object : PlatformFilePickerState {

        override fun launch(
            onPicked: (List<PickedDocument>) -> Unit,
        ) {
            onPicked(
                openDocuments(title = "Add books").map { file ->
                    PickedDocument(
                        fileName = file.name,
                        extension = file.extension,
                        location = file.absolutePath,
                    )
                },
            )
        }
    }
