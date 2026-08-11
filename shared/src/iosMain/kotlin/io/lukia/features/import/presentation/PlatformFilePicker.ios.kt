package io.lukia.features.import.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.lukia.features.import.domain.PickedDocument
import kotlinx.cinterop.ExperimentalForeignApi
import platform.darwin.NSObject
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIWindow
import platform.UniformTypeIdentifiers.UTTypeEPUB
import platform.UniformTypeIdentifiers.UTTypePDF

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberPlatformFilePicker(): PlatformFilePickerState {
    return remember {
        object : PlatformFilePickerState {

            private var delegate: UIDocumentPickerDelegateProtocol? = null

            override fun launch(
                onPicked: (PickedDocument?) -> Unit
            ) {
                val contentTypes = listOf(
                    UTTypePDF,
                    UTTypeEPUB
                )

                val picker = UIDocumentPickerViewController(
                    forOpeningContentTypes = contentTypes,
                    asCopy = true
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

                        val fileName = url.lastPathComponent ?: ""
                        val extension = url.pathExtension ?: ""
                        val location = url.absoluteString ?: ""

                        onPicked(
                            PickedDocument(
                                fileName = fileName,
                                extension = extension,
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