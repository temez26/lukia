package io.lukia.features.library.presentation.components.book

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.presentation.statusLabel
import io.lukia.resources.Res
import io.lukia.resources.book_on_server
import io.lukia.shared.presentation.components.icons.CloudGlyph
import io.lukia.shared.presentation.components.surface.BadgeTone
import io.lukia.shared.presentation.components.surface.StatusBadge
import org.jetbrains.compose.resources.stringResource

/** Why a book's file is not here: waiting on the server is an offer, a missing file is a problem. */
@Composable
internal fun BookStatusBadge(document: DocumentInfo, modifier: Modifier = Modifier) {
    StatusBadge(
        text = stringResource(document.statusLabel()),
        modifier = modifier,
        tone = if (document.awaitsDownload) BadgeTone.Accent else BadgeTone.Error,
    )
}

/** A small cloud beside a book's format and size: the sync server has this book too. */
@Composable
internal fun OnServerMark(modifier: Modifier = Modifier) {
    val label = stringResource(Res.string.book_on_server)
    CloudGlyph(
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier.size(MarkSize).semantics { contentDescription = label },
    )
}

/** Whether a book on the device is also on the server - what [OnServerMark] marks. */
internal val DocumentInfo.alsoOnServer: Boolean get() = exists && onServer

private val MarkSize = 14.dp
