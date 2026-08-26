package io.lukia.core.infrastructure

import io.lukia.core.domain.IncomingDocument
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.mp.KoinPlatformTools
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL

/**
 * A book handed to Lukia by another app, arriving from `onOpenURL` - see
 * `iOSApp.swift`.
 *
 * Called from Swift, which is why this is a plain top-level function taking a
 * string: a `DocumentInbox` is a Kotlin type the app shell has no reason to know
 * about, and the shell's whole job is to pass on a URL and forget it.
 *
 * The security scope is claimed and given back around the read rather than held.
 * A URL delivered this way is only readable between those two calls, and a scope
 * left open is a file the system cannot let go of; taking a copy inside the
 * window is exactly what the inbox is for.
 */
@OptIn(ExperimentalForeignApi::class)
fun deliverIncomingDocument(url: String) {
    val fileUrl = NSURL.URLWithString(url) ?: return
    val inbox = KoinPlatformTools.defaultContext().get().get<DocumentInbox>()

    inbox.deliver(
        IncomingDocument(
            fileName = fileUrl.lastPathComponent ?: FALLBACK_NAME,
            read = { fileUrl.readSecurityScoped() },
        ),
    )
}

/**
 * The file's bytes, read inside a security-scoped access.
 *
 * `startAccessingSecurityScopedResource` answering false is not on its own a
 * failure - it is what a URL already inside the app's own sandbox returns - so
 * the read is attempted either way and it is the read that decides.
 */
@OptIn(ExperimentalForeignApi::class)
private fun NSURL.readSecurityScoped(): ByteArray? {
    val scoped = startAccessingSecurityScopedResource()

    return try {
        NSData.dataWithContentsOfURL(this)?.toByteArray()
    } finally {
        if (scoped) stopAccessingSecurityScopedResource()
    }
}

/** For a URL with nothing name-like on the end. The import reports it. */
private const val FALLBACK_NAME = "Shared document"
