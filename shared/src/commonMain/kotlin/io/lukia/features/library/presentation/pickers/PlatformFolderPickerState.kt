package io.lukia.features.library.presentation.pickers

import io.lukia.core.domain.document.PickedFolder

interface PlatformFolderPickerState {
    fun launch(
        onPicked: (PickedFolder?) -> Unit,
    )
}
