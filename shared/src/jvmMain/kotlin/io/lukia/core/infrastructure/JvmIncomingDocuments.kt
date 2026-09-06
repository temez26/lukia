package io.lukia.core.infrastructure

import io.lukia.core.domain.document.IncomingDocument
import org.koin.mp.KoinPlatformTools
import java.io.File

/**
 * Puts books handed to the desktop app from outside it into the app's inbox,
 * for the library to take when it next looks.
 *
 * The desktop's two ways of being handed one, and the counterpart to
 * `Context.deliverIncomingDocuments` on Android and `deliverIncomingDocument` on
 * iOS: a file named on the command line - which is how every desktop opens a
 * document with an application - and a file dropped on the window.
 *
 * The inbox is resolved here rather than injected into the window, so that
 * `main` stays a thing that starts the app rather than a second place that knows
 * how the app is wired together.
 *
 * Anything that is not a file is dropped without a word. A reader who drags a
 * folder onto the window has not asked for every file under it to be imported,
 * and the library has a folder picker that says what it is about to do.
 */
fun deliverIncomingDocuments(files: List<File>) {
    val books = files.filter { it.isFile }

    if (books.isEmpty()) return

    val inbox = KoinPlatformTools.defaultContext().get().get<DocumentInbox>()

    books.forEach { file ->
        inbox.deliver(
            IncomingDocument(
                fileName = file.name,
                // Deferred and allowed to fail, exactly as the other two
                // platforms defer theirs: this is called from a drop callback
                // and from before the first frame, neither of which may be made
                // to wait on a hundred-megabyte PDF, and a file that has been
                // moved between the drop and the read is the import's news to
                // report rather than a throw out of the window's event thread.
                read = { runCatching { file.readBytes() }.getOrNull() },
            ),
        )
    }
}
