package io.lukia.features.import.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.lukia.core.domain.PickedFolder
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIWindow
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberPlatformFolderPicker(): PlatformFolderPickerState {
    return remember {
        object : PlatformFolderPickerState {

            private var delegate: UIDocumentPickerDelegateProtocol? = null

            /**
             * Held for the lifetime of the session rather than released after
             * the scan: the scanner and the analyzer both re-create their URLs
             * from [PickedFolder.location], and a URL rebuilt from a string
             * cannot re-acquire the security scope this one owns.
             */
            private var scopedFolders = mutableListOf<NSURL>()

            override fun launch(
                onPicked: (PickedFolder?) -> Unit
            ) {
                val contentTypes = listOf(
                    UTTypeFolder
                )

                // asCopy = false: copying would duplicate the entire library
                // folder into the app's sandbox.
                val picker = UIDocumentPickerViewController(
                    forOpeningContentTypes = contentTypes,
                    asCopy = false
                )

                val pickerDelegate = object :
                    NSObject(),
                    UIDocumentPickerDelegateProtocol {

                    override fun documentPicker(
                        controller: UIDocumentPickerViewController,
                        didPickDocumentsAtURLs: List<*>
                    ) {
                        val url = didPickDocumentsAtURLs
                            .firstOrNull() as? NSURL

                        if (url == null) {
                            onPicked(null)
                            return
                        }

                        if (url.startAccessingSecurityScopedResource()) {
                            scopedFolders.add(url)
                        }

                        val name = url.lastPathComponent ?: "Folder"
                        val location = url.absoluteString ?: ""

                        onPicked(
                            PickedFolder(
                                name = name,
                                location = location
                            )
                        )
                    }

                    override fun documentPickerWasCancelled(
                        controller: UIDocumentPickerViewController
                    ) {
                        onPicked(null)
                    }
                }

                delegate = pickerDelegate
                picker.delegate = pickerDelegate

                val window =
                    UIApplication.sharedApplication.windows
                        .firstOrNull() as? UIWindow

                val rootViewController =
                    window?.rootViewController

                rootViewController?.presentViewController(
                    picker,
                    animated = true,
                    completion = null
                )
            }
        }
    }
}
