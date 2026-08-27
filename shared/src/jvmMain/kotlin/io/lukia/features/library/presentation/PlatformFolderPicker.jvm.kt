package io.lukia.features.library.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.lukia.core.domain.PickedFolder
import io.lukia.core.infrastructure.isMacOs
import io.lukia.core.infrastructure.openFolder
import io.lukia.features.library.presentation.browser.DesktopPick
import io.lukia.features.library.presentation.browser.DesktopPicks

/**
 * Choosing where a library of books lives - see [rememberPlatformFilePicker],
 * which splits by platform for the same reason and in the same place.
 *
 * The split matters more here. `FileDialog` cannot select a directory at all off
 * macOS, so the only thing the other desktops ever had was Swing's chooser.
 */
@Composable
actual fun rememberPlatformFolderPicker(): PlatformFolderPickerState = remember {
    object : PlatformFolderPickerState {

        override fun launch(onPicked: (PickedFolder?) -> Unit) {
            if (!isMacOs) {
                DesktopPicks.ask(DesktopPick.Folder(onPicked))
                return
            }

            onPicked(
                openFolder(title = "Choose a library folder")?.let {
                    PickedFolder(name = it.name, location = it.absolutePath)
                },
            )
        }
    }
}
