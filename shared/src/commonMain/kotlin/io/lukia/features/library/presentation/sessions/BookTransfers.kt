package io.lukia.features.library.presentation.sessions

import androidx.compose.runtime.Stable
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.ServerBookDownloader
import io.lukia.core.domain.document.ServerBookUploader
import io.lukia.core.domain.runCatchingApp
import io.lukia.features.library.presentation.screens.BookTransfer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Moving books between this device and the sync server: a server book fetched
 * down, or a book on the device sent up - whichever it lacks. One transfer per
 * book, any number of books at once.
 *
 * Shared by the book's own page and the library's panel, so the two cannot
 * disagree about what pressing the button does.
 */
class BookTransfers(
    private val downloader: ServerBookDownloader,
    private val uploader: ServerBookUploader,
    private val scope: CoroutineScope,
) {
    private val _states = MutableStateFlow<Map<String, BookTransfer>>(emptyMap())

    /** Each book's transfer by location; a book not in it is idle. */
    val states: StateFlow<Map<String, BookTransfer>> = _states.asStateFlow()

    /** Whether a book on this device could be sent: there is a server, and the reader is signed in. */
    val canUpload: StateFlow<Boolean> = uploader.available.stateIn(scope, SharingStarted.Eagerly, false)

    private val jobs = mutableMapOf<String, Job>()
    private val perBook = mutableMapOf<String, MutableStateFlow<BookTransfer>>()

    /** One book's transfer, for a page about that book. */
    fun stateOf(location: String): StateFlow<BookTransfer> =
        perBook.getOrPut(location) { MutableStateFlow(_states.value[location] ?: BookTransfer.Idle) }.asStateFlow()

    /** Starts moving [document] whichever way it lacks; [onDone] once it has arrived. */
    fun start(document: DocumentInfo, onDone: () -> Unit = {}) {
        val location = document.document.location
        if (_states.value[location] is BookTransfer.Running) return
        set(location, BookTransfer.Running(progress = 0f))

        val onProgress: (Float) -> Unit = { set(location, BookTransfer.Running(it)) }
        jobs[location] = scope.launch {
            runCatchingApp {
                if (document.awaitsDownload) {
                    downloader.download(location, onProgress)
                } else {
                    uploader.upload(location, onProgress)
                }
            }
                .onSuccess {
                    set(location, null)
                    onDone()
                }
                .onFailure { set(location, BookTransfer.Failed) }
            jobs.remove(location)
        }
    }

    /** Stops the transfer; nothing of it is kept on either side. */
    fun cancel(location: String) {
        jobs.remove(location)?.cancel()
        set(location, null)
    }

    private fun set(location: String, state: BookTransfer?) {
        _states.update { if (state == null) it - location else it + (location to state) }
        perBook[location]?.value = state ?: BookTransfer.Idle
    }
}

/** A book's transfers as a screen draws them - see [BookTransfers]. */
@Stable
class BookTransferUi(
    val states: Map<String, BookTransfer> = emptyMap(),
    val canUpload: Boolean = false,
    val onStart: (DocumentInfo) -> Unit = {},
    val onCancel: (location: String) -> Unit = {},
) {
    fun stateOf(location: String): BookTransfer = states[location] ?: BookTransfer.Idle

    companion object {
        val None = BookTransferUi()
    }
}
