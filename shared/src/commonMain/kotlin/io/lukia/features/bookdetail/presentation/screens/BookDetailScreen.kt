package io.lukia.features.bookdetail.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lukia.features.bookdetail.presentation.BookDetailViewModel
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.presentation.displayTitle
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.dataOrNull
import io.lukia.core.presentation.displayType
import io.lukia.core.presentation.formattedSize
import io.lukia.shared.presentation.components.ActionMenuScrim
import io.lukia.shared.presentation.components.BackChevron
import io.lukia.shared.presentation.components.BookActionsMenu
import io.lukia.shared.presentation.components.BookCover
import io.lukia.shared.presentation.components.EllipsisGlyph
import io.lukia.shared.presentation.components.FavoriteBadge
import io.lukia.shared.presentation.components.GlassSurface
import io.lukia.shared.presentation.components.UiStateContent
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Vertical room reserved so content starts clear of the floating back control. */
private val TopChromeClearance: Dp = 84.dp

@Composable
fun BookDetailScreen(
    location: String,
    onBack: () -> Unit,
    viewModel: BookDetailViewModel = koinViewModel(parameters = { parametersOf(location) })
) {
    val uiState by viewModel.uiState.collectAsState()

    BookDetailContent(
        uiState = uiState,
        onBack = onBack,
        onRetry = viewModel::retry,
        onToggleFavorite = viewModel::toggleFavorite,
        // Nothing left on this screen once the entry is gone, so leaving is part
        // of the action rather than something the user is left to do.
        onDelete = {
            viewModel.deleteDocument()
            onBack()
        }
    )
}

/**
 * Stateless half of the screen. Split out so the layout can be rendered without
 * a ViewModel or dependency graph - used by the screenshot harness and useful
 * for previews.
 */
@Composable
fun BookDetailContent(
    uiState: UiState<DocumentInfo>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    val dimens = LocalLukiaDimens.current
    var menuExpanded by remember { mutableStateOf(false) }
    // The actions chrome is only meaningful once there is a book behind it.
    val currentDocument = uiState.dataOrNull

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // This screen has no Scaffold - it is deliberately full-bleed so
        // content passes under the floating back button - so window insets have
        // to be applied by hand. Without this the back control and the cover
        // sit underneath the status bar clock on phones.
        //
        // The background above stays outside the inset so colour still reaches
        // the screen edges. Resolves to zero on desktop.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            UiStateContent(
                state = uiState,
                // A document that failed to load may simply have been removed,
                // but a read that blew up can be worth a second attempt - so the
                // way out of the failure is a retry rather than a way back.
                actionLabel = "Try again",
                onAction = onRetry,
                modifier = Modifier.fillMaxSize()
            ) { document ->
                DocumentDetails(
                    document = document,
                    onLongPress = { menuExpanded = true }
                )
            }

            // Tap-outside-to-dismiss for the actions menu, which is a
            // non-focusable Popup and so cannot catch the press itself. Above
            // the content but below both floating controls.
            ActionMenuScrim(
                visible = menuExpanded,
                onDismiss = { menuExpanded = false }
            )

            // Floats above the scrolling content: this is the piece that most
            // wants the glass treatment, since content passes underneath it.
            GlassSurface(
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = dimens.screenPadding, top = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clickable(onClick = onBack)
                        .size(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BackChevron(
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Mirrors the back control across the top of the screen. Always
            // visible rather than hover-revealed as on a card: there is one book
            // here and room to show its actions outright.
            if (currentDocument != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = dimens.screenPadding, top = 16.dp)
                ) {
                    GlassSurface(shape = CircleShape) {
                        Box(
                            modifier = Modifier
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable(
                                    onClickLabel = "Book actions",
                                    onClick = { menuExpanded = true }
                                )
                                .size(44.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            EllipsisGlyph(
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    BookActionsMenu(
                        expanded = menuExpanded,
                        isFavorite = currentDocument.isFavorite,
                        onToggleFavorite = onToggleFavorite,
                        onDelete = onDelete,
                        onDismissRequest = { menuExpanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentDetails(
    document: DocumentInfo,
    onLongPress: () -> Unit
) {
    val dimens = LocalLukiaDimens.current
    val title = document.displayTitle()
    val haptics = LocalHapticFeedback.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(TopChromeClearance))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = dimens.maxDetailWidth)
                .fillMaxWidth()
                .padding(horizontal = dimens.screenPadding)
        ) {
            Box {
                BookCover(
                    coverImage = document.coverImage,
                    title = title,
                    // detectTapGestures rather than combinedClickable: the cover
                    // is not a button here, so it should carry the long press
                    // without gaining a ripple and a no-op tap.
                    modifier = Modifier
                        .width(dimens.detailCoverWidth)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onLongPress()
                                }
                            )
                        },
                    cornerRadius = 18.dp,
                    elevation = 20.dp,
                    monogramFontSize = 64.sp
                )

                FavoriteBadge(
                    visible = document.isFavorite,
                    size = 30.dp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = dimens.sectionSpacing)
            )

            Text(
                text = "${document.displayType()} · ${document.formattedSize()}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )

            if (!document.exists) {
                UnavailableNotice(modifier = Modifier.padding(top = 16.dp))
            }

            DetailInfoCard(
                document = document,
                modifier = Modifier.padding(top = dimens.sectionSpacing)
            )
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

/**
 * Grouped info list in the style of an iOS settings card: a single rounded
 * surface with inset hairline separators between rows.
 */
@Composable
private fun DetailInfoCard(
    document: DocumentInfo,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            InfoRow(label = "Format", value = document.displayType())

            RowSeparator()

            InfoRow(label = "Size", value = document.formattedSize())

            RowSeparator()

            StackedInfoRow(label = "Location", value = document.document.location)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

/**
 * Used for values too long to sit beside their label - a full file path or
 * content URI would otherwise be ellipsised down to nothing.
 */
@Composable
private fun StackedInfoRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun RowSeparator() {
    HorizontalDivider(
        thickness = Dp.Hairline,
        color = MaterialTheme.colorScheme.outlineVariant,
        // Inset from the left, matching how iOS aligns separators to the text.
        modifier = Modifier.padding(start = 16.dp)
    )
}

@Composable
private fun UnavailableNotice(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = "This file is no longer available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
}

