package io.lukia.core.infrastructure

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import io.lukia.core.domain.document.IncomingDocument
import org.koin.mp.KoinPlatformTools

/**
 * Puts whatever [intent] is carrying into the app's inbox, for the library to
 * take when it next looks.
 *
 * Called from the activity, which is the only thing given the grant that makes
 * a delivered document readable - and only for as long as it holds it. The
 * inbox is resolved here rather than injected there, so that `MainActivity`
 * stays a thing that starts the app rather than a second place that knows how
 * the app is wired together.
 */
fun Context.deliverIncomingDocuments(intent: Intent) {
    val inbox = KoinPlatformTools.defaultContext().get().get<DocumentInbox>()

    intent.incomingDocuments(this).forEach(inbox::deliver)
}

/**
 * The documents an intent is asking Lukia to open.
 *
 * Both shapes the system uses, because a reader does not distinguish them: a
 * `VIEW` is a file tapped somewhere that knows Lukia reads it, a `SEND` is one
 * pushed at Lukia from a share sheet, and either way somebody has a book they
 * want on their shelf. Anything else - a launch, a resume - is not carrying a
 * document and comes back empty.
 */
internal fun Intent.incomingDocuments(context: Context): List<IncomingDocument> {
    val uris = when (action) {
        Intent.ACTION_VIEW -> listOfNotNull(data)
        Intent.ACTION_SEND -> listOfNotNull(parcelableStream())
        Intent.ACTION_SEND_MULTIPLE -> parcelableStreams()
        else -> emptyList()
    }

    return uris.map { uri -> uri.asIncomingDocument(context) }
}

/**
 * One URI as something the app can keep.
 *
 * The read is deferred rather than done here because this is called from the
 * platform's own callback, which must not be made to wait on a hundred-megabyte
 * PDF. It has to happen soon all the same: an intent's grant lasts as long as
 * the task it was delivered to, so what reads this is the library, on the very
 * next turn - see `LibraryImportSession`.
 *
 * The application context is held rather than the activity's, so a read that
 * lands just after the activity goes is still made against something alive.
 */
private fun Uri.asIncomingDocument(context: Context): IncomingDocument {
    val resolver = context.applicationContext.contentResolver
    val uri = this

    return IncomingDocument(
        fileName = resolver.displayName(uri) ?: lastPathSegment ?: FALLBACK_NAME,
        read = { resolver.openInputStream(uri)?.use { it.readBytes() } },
    )
}

/**
 * What the provider calls the file. Taken from here rather than from the URI,
 * which carries a provider's own document id and says nothing about the name -
 * and the name is where the extension comes from, which is what decides whether
 * the app can read it at all.
 */
private fun ContentResolver.displayName(uri: Uri): String? =
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null

        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0) cursor.getString(index) else null
    }

@Suppress("DEPRECATION")
private fun Intent.parcelableStream(): Uri? = getParcelableExtra(Intent.EXTRA_STREAM)

@Suppress("DEPRECATION")
private fun Intent.parcelableStreams(): List<Uri> =
    getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()

/** For a provider that will not say. The import reports it as unreadable. */
private const val FALLBACK_NAME = "Shared document"
