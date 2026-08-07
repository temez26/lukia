package io.lukia.features.import.presentation

import androidx.compose.runtime.Composable
import io.lukia.features.import.domain.PickedDocument
import platform.Foundation.*
import platform.UIKit.*
import platform.darwin.*

@Composable
actual fun rememberPlatformFilePicker(): PlatformFilePickerState =
    object : PlatformFilePickerState {

        override fun launch(
            onPicked: (PickedDocument?) -> Unit
        ) {
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = null, didPickDocumentTypes = null)
            
            val delegate = object : NSObject() {
                override fun didPickDocuments(controller: UIDocumentPickerViewController, documents: List<*>) {
                    val url = (documents.firstOrNull() as? NSURL)?.URL
                    if (url != null) {
                        onPicked(PickedDocument(url = url.path))
                    } else {
                        onPicked(null)
                    }
                }

                override fun didCancel(controller: UIDocumentPickerViewController) {
                    onPicked(null)
                }
            }

            picker.delegate = delegate
            
            val window = UIApplication.sharedApplication.windows.firstOrNull as? UIWindow
            val rootViewController = window?.rootViewController
            rootViewController?.presentViewController(picker, animated = true, completion = null)
        }
    }