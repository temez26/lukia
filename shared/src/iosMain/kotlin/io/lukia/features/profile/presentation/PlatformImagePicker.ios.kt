package io.lukia.features.profile.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.lukia.core.infrastructure.toByteArray
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIWindow
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.darwin.NSObject

/**
 * The document picker asking for images, rather than `PHPicker`.
 *
 * `UTTypeImage` covers every format the system can decode, including the HEIC an
 * iPhone actually takes photographs in, and the picker reaches the photo library
 * through the Files app alongside everything else. It is also the control this
 * app already presents for choosing a file, so nothing new has to be learned -
 * and unlike `PHPicker` it needs no entry in the app's Info.plist to work.
 *
 * `asCopy` hands back a URL inside the app's own container, which is why the
 * bytes can simply be read: nothing here holds a permission on the reader's
 * library.
 */
@Composable
internal actual fun rememberPlatformImagePicker(): PlatformImagePickerState = remember {
    object : PlatformImagePickerState {

        private var delegate: UIDocumentPickerDelegateProtocol? = null

        override fun launch(onPicked: (ByteArray?) -> Unit) {
            val picker = UIDocumentPickerViewController(
                forOpeningContentTypes = listOf(UTTypeImage),
                asCopy = true,
            )

            picker.allowsMultipleSelection = false

            val pickerDelegate = object :
                NSObject(),
                UIDocumentPickerDelegateProtocol {

                override fun documentPicker(
                    controller: UIDocumentPickerViewController,
                    didPickDocumentsAtURLs: List<*>,
                ) {
                    val url = didPickDocumentsAtURLs.filterIsInstance<NSURL>().firstOrNull()

                    onPicked(url?.let { NSData.dataWithContentsOfURL(it)?.pictureBytes() })
                }

                override fun documentPickerWasCancelled(
                    controller: UIDocumentPickerViewController,
                ) {
                    onPicked(null)
                }
            }

            delegate = pickerDelegate
            picker.delegate = pickerDelegate

            val window = UIApplication.sharedApplication.windows.firstOrNull() as? UIWindow

            window?.rootViewController?.presentViewController(
                picker,
                animated = true,
                completion = null,
            )
        }
    }
}

/**
 * The bytes behind an [NSData], or null for an empty one.
 *
 * The copy itself is core's - see `NSDataBytes.kt`. What is this file's is the
 * null: a file that turned out to hold nothing is not a picture the reader
 * chose, and saying so here means nothing downstream has to test for an image
 * of no bytes.
 */
private fun NSData.pictureBytes(): ByteArray? = toByteArray().takeIf { it.isNotEmpty() }
