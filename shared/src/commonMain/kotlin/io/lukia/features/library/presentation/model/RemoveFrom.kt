package io.lukia.features.library.presentation.model

import io.lukia.core.domain.document.DocumentInfo

/** Where a book is taken away from: this device, the sync server's shared library, or both. */
enum class RemoveFrom(val device: Boolean, val server: Boolean) {
    Device(device = true, server = false),
    Server(device = false, server = true),
    DeviceAndServer(device = true, server = true),
    ;

    /**
     * Whether this does anything to [book]. A book only on the server has
     * nothing on this device to take; one never on the server, nothing there.
     */
    fun reaches(book: DocumentInfo): Boolean =
        (device && !book.awaitsDownload) || (server && book.onServer)
}

/**
 * The places these books can be removed from, in the order they are offered.
 * The server is only the admin's to remove from - see `ServerBookRemover`.
 */
fun List<DocumentInfo>.removalChoices(canRemoveFromServer: Boolean): List<RemoveFrom> {
    val onDevice = any { !it.awaitsDownload }
    val onServer = canRemoveFromServer && any { it.onServer }

    return buildList {
        if (onDevice) add(RemoveFrom.Device)
        if (onServer) add(RemoveFrom.Server)
        if (onDevice && onServer) add(RemoveFrom.DeviceAndServer)
    }
}
