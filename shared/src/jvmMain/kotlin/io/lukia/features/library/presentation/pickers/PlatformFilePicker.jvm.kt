package io.lukia.features.library.presentation.pickers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.lukia.core.domain.document.PickedDocument
import io.lukia.core.infrastructure.isMacOs
import io.lukia.core.infrastructure.openDocuments
import io.lukia.features.library.presentation.browser.DesktopPick
import io.lukia.features.library.presentation.browser.DesktopPicks

/**
 * The desktop's way of choosing books, which is not the same on every desktop.
 *
 * macOS opens `NSOpenPanel` - the Finder panel every other application there
 * opens, with the reader's own sidebar and recent places, and nothing this app
 * could draw would be better. Everywhere else the panel is drawn over the
 * library instead - see `PlatformPickerHost` - the alternative being a window
 * drawn by Java in neither the app's design nor the system's.
 */
@Composable
actual fun rememberPlatformFilePicker(): PlatformFilePickerState = remember {
    object : PlatformFilePickerState {

        override fun launch(onPicked: (List<PickedDocument>) -> Unit) {
            if (!isMacOs) {
                DesktopPicks.ask(DesktopPick.Books(onPicked))
                return
            }

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
}
