package io.lukia.features.import.presentation

import io.lukia.features.import.domain.PickedDocument

interface PlatformFilePickerState {
    fun launch(
        onPicked: (PickedDocument?) -> Unit
    )
}