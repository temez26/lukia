package io.lukia.features.import.presentation

import androidx.compose.runtime.Composable
import io.lukia.features.import.domain.PickedDocument
import platform.Foundation.*
import platform.UIKit.*
import platform.darwin.*
import platform.UniformTypeIdentifiers.*

@Composable
actual fun rememberPlatformFilePicker(): PlatformFilePickerState =
    object : PlatformFilePickerState {

        override fun launch(
            onPicked: (PickedDocument?) -> Unit
        ) {
            val contentTypes = listOf(UTType.pdfContentType, UTType.epubContentType)
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = contentTypes, didPickDocumentTypes = null)

            val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
                override fun documentPickerDidPickDocuments(controller: UIDocumentPickerViewController, documents: List<*>) {
                    val url = (documents.firstOrNull() as? NSURL)
                    if (url != null) {
                        val path = url.path ?: ""
                        val fileName = url.lastPathComponent ?: ""
                        val extension = url.pathExtension ?: ""
                        onPicked(PickedDocument(fileName, extension, path))
                    } else {
                        onPicked(null)
                    }
                }

                override fun documentPickerDidCancel(controller: UIDocumentPickerViewController) {
                    onPicked(null)
                }
            }

            picker.delegate = delegate

            val window = UIApplication.sharedApplication.windows.firstOrNull as? UIWindow
            val rootViewController = window?.rootViewController
            rootViewController?.presentViewController(picker, animated = true, completion = null)
        }
    }