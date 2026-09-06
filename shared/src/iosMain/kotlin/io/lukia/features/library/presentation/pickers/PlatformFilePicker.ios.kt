package io.lukia.features.library.presentation.pickers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.lukia.core.domain.document.PickedDocument
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeEPUB
import platform.UniformTypeIdentifiers.UTTypePDF
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.darwin.NSObject

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
    UTType.typeWithFilenameExtension("md"),
)

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberPlatformFilePicker(): PlatformFilePickerState {
    return remember {
        object : PlatformFilePickerState {

            private var delegate: UIDocumentPickerDelegateProtocol? = null

            override fun launch(
                onPicked: (List<PickedDocument>) -> Unit,
            ) {
                val contentTypes = supportedContentTypes()

                val picker = UIDocumentPickerViewController(
                    forOpeningContentTypes = contentTypes,
                    asCopy = true,
                )

                // A reader adding books has usually just saved several, and one
                // trip through Files should be enough for all of them.
                picker.allowsMultipleSelection = true

                val pickerDelegate = object :
                    NSObject(),
                    UIDocumentPickerDelegateProtocol {

                    override fun documentPicker(
                        controller: UIDocumentPickerViewController,
                        didPickDocumentsAtURLs: List<*>,
                    ) {
                        onPicked(
                            didPickDocumentsAtURLs
                                .filterIsInstance<NSURL>()
                                .mapNotNull { url ->
                                    PickedDocument(
                                        fileName = url.lastPathComponent ?: return@mapNotNull null,
                                        extension = url.pathExtension ?: "",
                                        location = url.absoluteString ?: return@mapNotNull null,
                                    )
                                },
                        )
                    }

                    override fun documentPickerWasCancelled(
                        controller: UIDocumentPickerViewController,
                    ) {
                        onPicked(emptyList())
                    }
                }

                delegate = pickerDelegate
                picker.delegate = pickerDelegate

                presentOverApp(picker)
            }
        }
    }
}
