package io.lukia.features.import.presentation

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.lukia.core.domain.PickedDocument

/**
 * What the system picker will let through.
 *
 * Markdown is claimed both ways on purpose: providers disagree about what a
 * `.md` file is, and one that reports it as plain text would otherwise be greyed
 * out in the picker for a format this app reads perfectly well. The text
 * wildcard covers the rest of that disagreement - `text/x-markdown` and the
 * like.
 *
 * `application/octet-stream` is here for the same reason and is the widest of
 * them: it is what a provider reports for a file it could not classify at all,
 * which on many devices is every EPUB and every Word document sitting in
 * Downloads. It lets through a few files this app cannot read, and the import
 * says so plainly when it meets one - a better trade than a picker that greys
 * out the book the reader is pointing at.
 */
private val SUPPORTED_MIME_TYPES = arrayOf(
    "application/pdf",
    "application/epub+zip",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "text/plain",
    "text/markdown",
    "text/*",
    "application/octet-stream"
)

/**
 * The stock contract, asking for a grant that outlives the process.
 *
 * Without the flag the picker hands back a URI readable only until the app is
 * killed, and the library stores locations to open days later - so a book
 * imported this evening would fail to open tomorrow with nothing on screen
 * explaining why. `ACTION_OPEN_DOCUMENT_TREE` is granted this way by the system
 * already, which is why only the file picker has to ask.
 */
private class OpenPersistableDocuments :
    ActivityResultContracts.OpenMultipleDocuments() {

    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input)
            .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
}

@Composable
actual fun rememberPlatformFilePicker(): PlatformFilePickerState {

    val context = LocalContext.current

    val callback = remember {
        mutableStateOf<(List<PickedDocument>) -> Unit>({})
    }

    val contract = remember { OpenPersistableDocuments() }

    val launcher =
        rememberLauncherForActivityResult(contract = contract) { uris ->

            val resolver = context.contentResolver

            callback.value(
                uris.map { uri ->
                    // Some providers refuse to persist, and the system caps how
                    // many grants an app may hold - neither is a reason to drop
                    // the book, which is readable now either way.
                    runCatching {
                        resolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }

                    uri.asPickedDocument(resolver)
                }
            )
        }

    return object : PlatformFilePickerState {

        override fun launch(
            onPicked: (List<PickedDocument>) -> Unit
        ) {

            callback.value = onPicked

            launcher.launch(SUPPORTED_MIME_TYPES)
        }
    }
}

/**
 * A content URI as the import knows it. The display name is where the extension
 * comes from - the URI itself carries a provider's document id, which says
 * nothing about the file.
 */
private fun Uri.asPickedDocument(resolver: ContentResolver): PickedDocument {

    var fileName = "Unknown"

    resolver.query(
        this,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor ->

        if (cursor.moveToFirst()) {

            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)

            if (index >= 0) {
                fileName = cursor.getString(index)
            }
        }
    }

    return PickedDocument(
        fileName = fileName,
        extension = fileName.substringAfterLast('.', ""),
        location = toString()
    )
}
