package io.lukia.features.profile.presentation

import androidx.compose.runtime.Composable

/**
 * Choosing one picture from the device.
 *
 * Apart from the document picker rather than a mode of it, because almost
 * nothing about the two is the same: that one takes several files, filters for
 * the formats this app can read as books, and hands back locations to be opened
 * again later. This one takes exactly one image and hands back its bytes.
 *
 * Bytes rather than a location on purpose. The picture is copied into the app
 * the instant it is chosen, so nothing has to hold a permission for somebody
 * else's file, and a photograph the reader later moves or deletes does not take
 * their avatar with it.
 */
interface PlatformImagePickerState {

    /** Hands back null for a cancelled picker, and for a file that could not be read. */
    fun launch(onPicked: (ByteArray?) -> Unit)
}

@Composable
expect fun rememberPlatformImagePicker(): PlatformImagePickerState
