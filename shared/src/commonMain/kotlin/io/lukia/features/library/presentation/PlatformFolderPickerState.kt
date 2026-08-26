package io.lukia.features.library.presentation

import io.lukia.core.domain.PickedFolder

interface PlatformFolderPickerState {
    fun launch(
        onPicked: (PickedFolder?) -> Unit,
    )
}
