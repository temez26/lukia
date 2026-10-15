package io.lukia.features.library.presentation.components.book

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.presentation.statusLabel
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
