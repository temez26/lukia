package io.lukia.features.import.presentation

import io.lukia.core.domain.PickedFolder

interface PlatformFolderPickerState {
    fun launch(
        onPicked: (PickedFolder?) -> Unit
    )
}
