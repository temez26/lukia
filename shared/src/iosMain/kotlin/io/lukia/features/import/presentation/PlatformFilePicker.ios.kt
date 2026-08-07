package io.lukia.features.import.presentation

import androidx.compose.runtime.Composable
import io.lukia.features.import.domain.PickedDocument

@Composable
actual fun rememberPlatformFilePicker(): PlatformFilePickerState =
    object : PlatformFilePickerState {

        override fun launch(
            onPicked: (PickedDocument?) -> Unit
        ) {
            TODO("Implement iOS UIDocumentPicker")
        }
    }