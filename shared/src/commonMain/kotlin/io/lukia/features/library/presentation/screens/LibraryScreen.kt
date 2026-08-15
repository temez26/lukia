package io.lukia.features.library.presentation.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.presentation.UiState
import io.lukia.features.import.presentation.rememberPlatformFilePicker
import io.lukia.features.import.presentation.rememberPlatformFolderPicker
import io.lukia.features.library.presentation.LibraryViewModel
import io.lukia.features.library.presentation.components.BookCard
import io.lukia.shared.presentation.components.ActionMenuScrim
import io.lukia.shared.presentation.components.BOOK_COVER_ASPECT_RATIO
import io.lukia.shared.presentation.components.ExpandableActionMenu
import io.lukia.shared.presentation.components.FolderGlyph
import io.lukia.shared.presentation.components.LoadingState
import io.lukia.shared.presentation.components.PlusGlyph
import io.lukia.shared.presentation.components.QuickAction
import io.lukia.shared.presentation.components.UiStateContent
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = koinViewModel(),
    onDocumentClick: (DocumentInfo) -> Unit = {},
    onDocumentDetails: (DocumentInfo) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()

    val filePicker = rememberPlatformFilePicker()
    val folderPicker = rememberPlatformFolderPicker()
    val snackbarHostState = remember { SnackbarHostState() }

    // Fires again each time the grid is re-entered from the detail screen, which
    // may have favourited or removed a book behind its back.
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    LibraryContent(
        uiState = uiState,
        onImport = {
            filePicker.launch { pickedDocument ->
                pickedDocument?.let { viewModel.importDocument(it) }
            }
        },
        onImportFolder = {
            folderPicker.launch { pickedFolder ->
                pickedFolder?.let { viewModel.importFolder(it) }
            }
        },
        onDocumentClick = onDocumentClick,
        onDocumentDetails = onDocumentDetails,
        onToggleFavorite = viewModel::toggleFavorite,
        onDelete = viewModel::deleteDocument,
        onRetry = viewModel::refresh,
        isImporting = isImporting,
        snackbarHostState = snackbarHostState
    )
}

/**
 * Stateless half of the screen. Split out so the layout can be rendered without
 * a ViewModel or dependency graph - used by the screenshot harness and useful
 * for previews.
 */
@Composable
fun LibraryContent(
    uiState: UiState<List<DocumentInfo>>,
    onImport: () -> Unit,
    onImportFolder: () -> Unit,
    onDocumentClick: (DocumentInfo) -> Unit,
    onDocumentDetails: (DocumentInfo) -> Unit,
    onToggleFavorite: (DocumentInfo) -> Unit,
    onDelete: (DocumentInfo) -> Unit,
    onRetry: () -> Unit,
    isImporting: Boolean,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    initialActionMenuExpanded: Boolean = false
) {
    val dimens = LocalLukiaDimens.current
    var actionMenuExpanded by rememberSaveable { mutableStateOf(initialActionMenuExpanded) }
    // Held here rather than inside the grid so the card menu's scrim can sit in
    // the same content layer as the action menu's. One location at a time, so
    // opening a menu closes any other.
    var menuFor by remember { mutableStateOf<String?>(null) }

    val actions = listOf(
        QuickAction(
            label = "Import book",
            onClick = onImport,
            icon = { tint -> PlusGlyph(tint = tint, modifier = Modifier.size(18.dp)) }
        ),
        QuickAction(
            label = "Add folder",
            onClick = onImportFolder,
            icon = { tint -> FolderGlyph(tint = tint, modifier = Modifier.size(18.dp)) }
        )
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExpandableActionMenu(
                actions = actions,
                expanded = actionMenuExpanded,
                onExpandedChange = { actionMenuExpanded = it }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.TopCenter
            ) {
                // Reading the library is the outer state; everything inside is
                // what a successfully read library can look like.
                //
                // The width cap lives on the container, not the children: put
                // it on the children and AnimatedContent's own sizing wins,
                // which left-aligns the grid instead of centring it.
                UiStateContent(
                    state = uiState,
                    actionLabel = "Try again",
                    onAction = onRetry,
                    modifier = Modifier
                        // Capped first, then filled, so wide desktop windows keep
                        // a readable measure instead of stretching edge to edge.
                        .widthIn(max = dimens.maxGridWidth)
                        .fillMaxSize()
                ) { documents ->
                    // An import that lands books keeps the grid up and reports
                    // itself in the header instead; the importing state below is
                    // only for when there is nothing else to show yet.
                    val contentState = when {
                        documents.isNotEmpty() -> LibraryContentState.Content
                        isImporting -> LibraryContentState.Importing
                        else -> LibraryContentState.Empty
                    }

                    // Cross-dissolve rather than a hard swap when the first book
                    // lands. The slight scale keeps it from feeling like a cut.
                    AnimatedContent(
                        targetState = contentState,
                        transitionSpec = {
                            val enter = fadeIn(tween(durationMillis = 280)) +
                                scaleIn(tween(durationMillis = 280), initialScale = 0.96f)
                            val exit = fadeOut(tween(durationMillis = 180))
                            // `using null` disables the default SizeTransform,
                            // which would otherwise animate the container bounds
                            // and slide the content sideways mid-transition.
                            (enter togetherWith exit) using null
                        },
                        contentAlignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize(),
                        label = "libraryContent"
                    ) { state ->
                        when (state) {
                            LibraryContentState.Importing -> LoadingState(
                                message = "Importing books…",
                                modifier = Modifier.fillMaxSize()
                            )

                            LibraryContentState.Empty -> LibraryEmptyState(
                                onImport = onImport,
                                modifier = Modifier.fillMaxSize()
                            )

                            LibraryContentState.Content -> LibraryGrid(
                                documents = documents,
                                isImporting = isImporting,
                                onDocumentClick = onDocumentClick,
                                onDocumentDetails = onDocumentDetails,
                                menuFor = menuFor,
                                onMenuForChange = { menuFor = it },
                                onToggleFavorite = onToggleFavorite,
                                onDelete = onDelete,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            // Sits above page content but below the action menu, which Scaffold
            // draws in its own layer on top.
            ActionMenuScrim(
                visible = actionMenuExpanded,
                onDismiss = { actionMenuExpanded = false }
            )

            // The card menu is a Popup, so it draws above this scrim without
            // needing to share its layer. The focusable popup already dismisses
            // itself on an outside tap; this is the dimming that goes with it.
            ActionMenuScrim(
                visible = menuFor != null,
                onDismiss = { menuFor = null }
            )
        }
    }
}

/** What a successfully read library is showing. */
private enum class LibraryContentState {
    Importing,
    Empty,
    Content
}

@Composable
private fun LibraryGrid(
    documents: List<DocumentInfo>,
    isImporting: Boolean,
    onDocumentClick: (DocumentInfo) -> Unit,
    onDocumentDetails: (DocumentInfo) -> Unit,
    menuFor: String?,
    onMenuForChange: (String?) -> Unit,
    onToggleFavorite: (DocumentInfo) -> Unit,
    onDelete: (DocumentInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = LocalLukiaDimens.current

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = dimens.bookCellMinWidth),
        modifier = modifier,
        contentPadding = PaddingValues(
            start = dimens.screenPadding,
            end = dimens.screenPadding,
            top = 8.dp,
            // Clears the floating import button so the last row is never hidden.
            bottom = 104.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(dimens.gridSpacing),
        verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            LibraryHeader(bookCount = documents.size, isImporting = isImporting)
        }

        items(
            items = documents,
            // Location identifies a document in the library, so keying on it
            // lets Compose track a card across reloads instead of rebuilding
            // the whole grid whenever an import appends to the list.
            key = { it.document.location }
        ) { document ->
            BookCard(
                document = document,
                menuExpanded = menuFor == document.document.location,
                onClick = { onDocumentClick(document) },
                onMenuRequest = { onMenuForChange(document.document.location) },
                onMenuDismiss = { onMenuForChange(null) },
                onToggleFavorite = { onToggleFavorite(document) },
                onDelete = { onDelete(document) },
                onShowDetails = { onDocumentDetails(document) },
                modifier = Modifier.animateItem()
            )
        }
    }
}

@Composable
private fun LibraryHeader(bookCount: Int, isImporting: Boolean) {
    Column(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
        Text(
            text = "Library",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        // An import into a library that already has books reports itself here
        // rather than replacing the grid - the books already on screen stay
        // readable while the rest arrive.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp)
        ) {
            if (isImporting) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Text(
                text = when {
                    isImporting -> "Importing…"
                    bookCount == 1 -> "1 book"
                    else -> "$bookCount books"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The empty-state cover. Tapping it starts an import, so the most prominent
 * thing on an empty screen is also actionable rather than purely decorative.
 *
 * The tap animation hinges the cover on its left edge and swings it open like a
 * real book before settling back with a soft spring.
 */
@Composable
private fun EmptyCoverPlaceholder(onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val scope = rememberCoroutineScope()
    val openAngle = remember { Animatable(0f) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )
    // Desktop-only in practice: touch platforms never report hover, so this
    // stays at rest on phones and tablets without needing a platform check.
    val hover by animateFloatAsState(
        targetValue = if (isHovered) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = Spring.StiffnessMediumLow
        )
    )
    // The plus warms to the accent colour on hover, confirming the card is a
    // control rather than an empty slot.
    val glyphTint by animateColorAsState(
        targetValue = if (isHovered) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        },
        animationSpec = tween(durationMillis = 200)
    )

    Box(
        modifier = Modifier
            .width(104.dp)
            .aspectRatio(BOOK_COVER_ASPECT_RATIO)
            .graphicsLayer {
                rotationY = openAngle.value
                val scale = pressScale * (1f + 0.04f * hover)
                scaleX = scale
                scaleY = scale
                translationY = -8.dp.toPx() * hover
                // Hinge on the left edge, where a book's spine would be.
                transformOrigin = TransformOrigin(0f, 0.5f)
                // Without a wider camera distance the rotation reads as a
                // horizontal squash instead of a swing.
                cameraDistance = 16f * density
            }
            // Shadow grows in only on hover, so the resting state stays as flat
            // as the placeholder it is.
            .shadow(
                elevation = lerp(0.dp, 16.dp, hover),
                shape = shape,
                clip = false
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = shape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = "Import a book"
            ) {
                scope.launch {
                    openAngle.animateTo(
                        targetValue = -34f,
                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                    )
                    // Fired mid-animation so the swing is visible before a
                    // platform file dialog takes over the window.
                    onClick()
                    openAngle.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        PlusGlyph(
            tint = glyphTint,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun LibraryEmptyState(
    onImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = LocalLukiaDimens.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = dimens.screenPadding)
    ) {
        // Without this the empty screen has no title at all, and the layout
        // jumps once the first book lands.
        Text(
            text = "Library",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 20.dp)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 320.dp)
        ) {
            // A blank cover stands in for "a book could go here" - and doubles
            // as the import affordance.
            EmptyCoverPlaceholder(onClick = onImport)

            Text(
                text = "Your library is empty",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 28.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // With no explicit button here, the copy has to carry the
            // affordance for the tappable cover above.
            Text(
                text = "Tap the cover to import a PDF or EPUB.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
