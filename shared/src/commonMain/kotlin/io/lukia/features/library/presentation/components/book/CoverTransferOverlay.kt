package io.lukia.features.library.presentation.components.book

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.features.library.presentation.screens.BookTransfer
import io.lukia.resources.Res
import io.lukia.resources.book_download_failed
import io.lukia.resources.book_downloading
import io.lukia.resources.reader_percent
import io.lukia.shared.presentation.theme.LukiaAlpha
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/** The ring drawn while a book comes down: big enough to read at a glance on a small cover. */
private val RingSize = 40.dp
private val RingStroke = 3.dp
private val MarkSize = 16.dp

/**
 * What a cover says about where a book is kept: only on the server or there as
 * well as here, how far a download has got, or that one failed.
 *
 * On the cover rather than beside it, because the cover is what was pressed.
 * Opening a book that is only on the server downloads it first, and that used
 * to be a wait with nothing on screen - a reader pressed again, or gave up,
 * having no way to know anything was happening.
 *
 * Callers place this inside whatever clips the cover, as with the reading
 * progress overlay.
 */
@Composable
internal fun CoverTransferOverlay(
    document: DocumentInfo,
    transfer: BookTransfer?,
    modifier: Modifier = Modifier,
) {
    when (transfer) {
        is BookTransfer.Running -> {
            val percent = (transfer.progress * PercentScale).toInt()
            val spoken = stringResource(Res.string.book_downloading) + " " +
                stringResource(Res.string.reader_percent, percent)

            CoverPanel(modifier.semantics { contentDescription = spoken }) {
                CircularProgressIndicator(
                    progress = { transfer.progress },
                    strokeWidth = RingStroke,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = LukiaAlpha.Badge),
                    modifier = Modifier.size(RingSize),
                )
                Text(
                    text = stringResource(Res.string.reader_percent, percent),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // Pressing the cover again tries again, so this needs no button of its own.
        BookTransfer.Failed -> CoverPanel(modifier) {
            Text(
                text = stringResource(Res.string.book_download_failed),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }

        // The same mark the shelf's captions carry, so a cloud means the
        // server on every screen - see [StorageMark].
        else -> if (document.storage.isMarked) {
            Box(modifier = modifier.fillMaxSize()) {
                StorageMark(
                    document = document,
                    size = MarkSize,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Space.sm)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = LukiaAlpha.OverArtwork))
                        .padding(Space.xs),
                )
            }
        }
    }
}

/** The cover washed out behind what is said about it, so the words read over any artwork. */
@Composable
private fun CoverPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.sm, Alignment.CenterVertically),
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = LukiaAlpha.OverArtwork))
            .padding(Space.md),
    ) {
        content()
    }
}

private const val PercentScale = 100
