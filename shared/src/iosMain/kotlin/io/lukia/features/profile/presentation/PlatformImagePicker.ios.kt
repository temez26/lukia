package io.lukia.features.profile.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIWindow
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.darwin.NSObject
import platform.posix.memcpy

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
@OptIn(ExperimentalForeignApi::class)
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

                    onPicked(url?.let { NSData.dataWithContentsOfURL(it)?.toByteArray() })
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
 * The bytes behind an [NSData], copied out once.
 *
 * Pinned for the length of the copy so the collector cannot move the array out
 * from under `memcpy`. An empty data has no first byte to address, which is why
 * it is answered before any of that.
 */
@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray? {
    val size = length.toInt()

    if (size <= 0) return null

    return ByteArray(size).apply {
        usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
}
