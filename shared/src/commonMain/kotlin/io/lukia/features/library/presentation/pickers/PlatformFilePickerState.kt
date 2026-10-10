package io.lukia.features.library.presentation.pickers

import io.lukia.core.domain.document.PickedDocument

interface PlatformFilePickerState {
    /**
     * Opens the platform's file picker and hands back everything chosen.
     *
     * A list rather than a single file: a reader adding books has usually just
     * downloaded several, and picking them one at a time means reopening the
     * dialog once per book. An empty list is a cancelled picker - there is
     * nothing for a caller to do about it that it would not also do for a
     * selection of none.
     */
    fun launch(
        onPicked: (List<PickedDocument>) -> Unit,
    )
}
