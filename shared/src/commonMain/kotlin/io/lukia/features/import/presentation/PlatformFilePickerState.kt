package io.lukia.features.import.presentation

import io.lukia.core.domain.PickedDocument

interface PlatformFilePickerState {
    fun launch(
        onPicked: (PickedDocument?) -> Unit
    )
}