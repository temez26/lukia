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
import io.lukia.resources.Res
import io.lukia.resources.action_cancel
import io.lukia.resources.action_try_again
import io.lukia.resources.book_download
import io.lukia.resources.book_download_failed
import io.lukia.resources.book_downloading
import io.lukia.resources.reader_percent
import io.lukia.shared.presentation.components.controls.ButtonVariant
import io.lukia.shared.presentation.components.controls.LukiaButton
import io.lukia.shared.presentation.components.controls.ReadingProgressBar
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/** Where fetching a server book onto the device has got to - see `ServerBookDownloader`. */
sealed interface BookDownload {

    /** Not started, or cancelled. */
    data object Idle : BookDownload

    /** Arriving; [progress] from 0 to 1. */
    data class Running(val progress: Float) : BookDownload

    /** Stopped before the end - offline, server gone. Nothing was kept, so trying again starts over. */
    data object Failed : BookDownload
}

/** What the download row can be asked to do. */
class BookDownloadActions(
    val onStart: () -> Unit = {},
    val onCancel: () -> Unit = {},
)

/**
 * The control a server book not downloaded yet has in place of reading it:
 * a button, then progress that can be cancelled, then - only if it failed - a
 * way to try again.
 */
@Composable
internal fun BookDownloadRow(state: BookDownload, actions: BookDownloadActions, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.sm),
        modifier = modifier,
    ) {
        when (state) {
            BookDownload.Idle -> LukiaButton(
                label = stringResource(Res.string.book_download),
                onClick = actions.onStart,
            )

            is BookDownload.Running -> {
                Text(
                    text = stringResource(Res.string.book_downloading) + " " +
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

            BookDownload.Failed -> {
                Text(
                    text = stringResource(Res.string.book_download_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                LukiaButton(label = stringResource(Res.string.action_try_again), onClick = actions.onStart)
            }
        }
    }
}

private const val PercentScale = 100
private val BarWidth = 180.dp
private val BarHeight = 4.dp
