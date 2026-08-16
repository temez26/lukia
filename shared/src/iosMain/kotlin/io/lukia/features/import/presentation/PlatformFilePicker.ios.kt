package io.lukia.features.import.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.lukia.core.domain.PickedDocument
import kotlinx.cinterop.ExperimentalForeignApi
import platform.darwin.NSObject
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIWindow
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeEPUB
import platform.UniformTypeIdentifiers.UTTypePDF
import platform.UniformTypeIdentifiers.UTTypePlainText

/**
 * The types the document picker will let through.
 *
 * Word documents and Markdown have no constant of their own in
 * `UniformTypeIdentifiers`, so they are looked up by extension - which resolves
 * to whatever the system has registered for them, and to nothing at all on a
 * system that has registered neither. Anything that does not resolve is dropped
 * rather than passed on: a null in this list would be a picker that shows no
 * files at all.
 */
@OptIn(ExperimentalForeignApi::class)
private fun supportedContentTypes(): List<UTType> = listOfNotNull(
    UTTypePDF,
    UTTypeEPUB,
    UTTypePlainText,
    UTType.typeWithFilenameExtension("docx"),
    UTType.typeWithFilenameExtension("md")
)

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberPlatformFilePicker(): PlatformFilePickerState {
    return remember {
        object : PlatformFilePickerState {

            private var delegate: UIDocumentPickerDelegateProtocol? = null

            override fun launch(
                onPicked: (PickedDocument?) -> Unit
            ) {
                val contentTypes = supportedContentTypes()

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