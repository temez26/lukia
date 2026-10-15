package io.lukia.features.library.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.resources.Res
import io.lukia.resources.action_cancel
import io.lukia.resources.action_try_again
import io.lukia.resources.book_download
import io.lukia.resources.book_download_failed
import io.lukia.resources.book_downloading
import io.lukia.resources.book_upload
import io.lukia.resources.book_upload_failed
import io.lukia.resources.book_uploading
import io.lukia.resources.reader_percent
import io.lukia.shared.presentation.components.controls.ButtonVariant
import io.lukia.shared.presentation.components.controls.LukiaButton
import io.lukia.shared.presentation.components.controls.ReadingProgressBar
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Which way a book is going: from the server onto the device, or the other way. */
enum class TransferDirection { Download, Upload }

/**
 * Where moving a book between the device and the sync server has got to - see
 * `ServerBookDownloader` and `ServerBookUploader`.
 */
sealed interface BookTransfer {

    /** Not started, or cancelled. */
    data object Idle : BookTransfer

    /** Arriving; [progress] from 0 to 1. */
    data class Running(val progress: Float) : BookTransfer

    /** Stopped before the end - offline, server gone. Nothing was kept, so trying again starts over. */
    data object Failed : BookTransfer
}

/** What the download row can be asked to do. */
class BookTransferActions(
    val onStart: () -> Unit = {},
    val onCancel: () -> Unit = {},
)

/**
 * The control for a book that is only on one side: a server book not
 * downloaded yet, or a book on the device the server does not have. A button,
 * then progress that can be cancelled, then - only if it failed - a way to try
 * again.
 */
@Composable
internal fun BookTransferRow(
    direction: TransferDirection,
    state: BookTransfer,
    actions: BookTransferActions,
    modifier: Modifier = Modifier,
) {
    val labels = TransferLabels.of(direction)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.sm),
        modifier = modifier,
    ) {
        when (state) {
            BookTransfer.Idle -> LukiaButton(
                label = stringResource(labels.start),
                onClick = actions.onStart,
            )

            is BookTransfer.Running -> {
                Text(
                    text = stringResource(labels.running) + " " +
                        stringResource(Res.string.reader_percent, (state.progress * PercentScale).toInt()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ReadingProgressBar(fraction = state.progress, modifier = Modifier.width(BarWidth).height(BarHeight))
                LukiaButton(
                    label = stringResource(Res.string.action_cancel),
                    onClick = actions.onCancel,
                    variant = ButtonVariant.Secondary,
                )
            }

            BookTransfer.Failed -> {
                Text(
                    text = stringResource(labels.failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                LukiaButton(label = stringResource(Res.string.action_try_again), onClick = actions.onStart)
            }
        }
    }
}

/** Which way [this] book could go, or null when it is on both sides or there is no server to send it to. */
fun DocumentInfo.transferDirection(canUpload: Boolean): TransferDirection? = when {
    awaitsDownload -> TransferDirection.Download
    canUpload && exists && !onServer -> TransferDirection.Upload
    else -> null
}

/** What the row says, for the way the book is going. */
private class TransferLabels(val start: StringResource, val running: StringResource, val failed: StringResource) {
    companion object {
        fun of(direction: TransferDirection) = when (direction) {
            TransferDirection.Download -> Download
            TransferDirection.Upload -> Upload
        }

        private val Download = TransferLabels(
            Res.string.book_download,
            Res.string.book_downloading,
            Res.string.book_download_failed,
        )
        private val Upload = TransferLabels(
            Res.string.book_upload,
            Res.string.book_uploading,
            Res.string.book_upload_failed,
        )
    }
}

private const val PercentScale = 100
private val BarWidth = 180.dp
private val BarHeight = 4.dp
