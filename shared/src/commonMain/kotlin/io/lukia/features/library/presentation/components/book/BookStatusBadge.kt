package io.lukia.features.library.presentation.components.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.domain.document.BookStorage
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.presentation.statusLabel
import io.lukia.features.library.presentation.screens.BookTransfer
import io.lukia.resources.Res
import io.lukia.resources.book_download_failed
import io.lukia.resources.book_downloading
import io.lukia.resources.book_on_device_and_server
import io.lukia.resources.book_sync_device_only
import io.lukia.resources.book_sync_not_downloaded
import io.lukia.resources.book_unavailable
import io.lukia.resources.reader_percent
import io.lukia.shared.presentation.components.icons.CloudGlyph
import io.lukia.shared.presentation.components.icons.DeviceGlyph
import io.lukia.shared.presentation.components.surface.BadgeTone
import io.lukia.shared.presentation.components.surface.StatusBadge
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Why a book's file is not here: waiting on the server is an offer, a missing
 * file is a problem - and a book on its way down says how far it has got.
 */
@Composable
internal fun BookStatusBadge(document: DocumentInfo, modifier: Modifier = Modifier, transfer: BookTransfer? = null) {
    val (text, tone) = when (transfer) {
        is BookTransfer.Running -> stringResource(Res.string.book_downloading) + " " +
            stringResource(Res.string.reader_percent, (transfer.progress * PercentScale).toInt()) to BadgeTone.Accent
        BookTransfer.Failed -> stringResource(Res.string.book_download_failed) to BadgeTone.Error
        else -> stringResource(document.statusLabel()) to
            if (document.awaitsDownload) BadgeTone.Accent else BadgeTone.Error
    }
    StatusBadge(text = text, modifier = modifier, tone = tone)
}

private const val PercentScale = 100

/**
 * Where a book's file is kept, drawn the same on every screen: a cloud for a book
 * only on the server, a phone beside the cloud for one on this device and the
 * server both.
 *
 * A book only on this device carries no mark - that is where every book is until
 * a server is involved, and a phone on every cover would say nothing.
 */
@Composable
internal fun StorageMark(
    document: DocumentInfo,
    modifier: Modifier = Modifier,
    size: Dp = MarkSize,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    val storage = document.storage
    if (!storage.isMarked) return

    val label = stringResource(storage.label())

    StorageGlyphs(
        storage = storage,
        size = size,
        tint = tint,
        modifier = modifier.semantics { contentDescription = label },
    )
}

/** The phone, the cloud or both - nothing for a book that is nowhere to be read. */
@Composable
private fun StorageGlyphs(storage: BookStorage, size: Dp, tint: Color, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MarkGap),
        modifier = modifier,
    ) {
        if (storage == BookStorage.Device || storage == BookStorage.Both) {
            DeviceGlyph(tint = tint, modifier = Modifier.size(size))
        }
        if (storage == BookStorage.Server || storage == BookStorage.Both) {
            CloudGlyph(tint = tint, modifier = Modifier.size(size))
        }
    }
}

/** Whether [StorageMark] draws anything for a book kept like this. */
internal val BookStorage.isMarked: Boolean get() = this == BookStorage.Server || this == BookStorage.Both

/** Where a book is kept, said in words. */
internal fun BookStorage.label(): StringResource = when (this) {
    BookStorage.Device -> Res.string.book_sync_device_only
    BookStorage.Server -> Res.string.book_sync_not_downloaded
    BookStorage.Both -> Res.string.book_on_device_and_server
    BookStorage.Missing -> Res.string.book_unavailable
}

private val MarkSize = 14.dp

/** Close enough that a phone and a cloud read as one mark rather than two. */
private val MarkGap = 1.dp
