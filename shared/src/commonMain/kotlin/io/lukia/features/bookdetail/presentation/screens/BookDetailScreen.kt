package io.lukia.features.bookdetail.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.dataOrNull
import io.lukia.core.presentation.displayType
import io.lukia.core.presentation.formattedSize
import io.lukia.features.bookdetail.application.BookDetails
import io.lukia.features.bookdetail.presentation.BookDetailViewModel
import io.lukia.features.bookdetail.presentation.authorLine
import io.lukia.features.bookdetail.presentation.bookTitle
import io.lukia.features.bookdetail.presentation.components.ReadableCover
import io.lukia.features.bookdetail.presentation.languageName
import io.lukia.features.bookdetail.presentation.lengthLabel
import io.lukia.features.bookdetail.presentation.progressLabel
import io.lukia.features.bookdetail.presentation.publishedYear
import io.lukia.features.bookdetail.presentation.readActionLabel
import io.lukia.shared.presentation.components.ActionMenuScrim
import io.lukia.shared.presentation.components.BackChevron
import io.lukia.shared.presentation.components.BadgeTone
import io.lukia.shared.presentation.components.BookActionsMenu
import io.lukia.shared.presentation.components.ButtonSize
import io.lukia.shared.presentation.components.ButtonVariant
import io.lukia.shared.presentation.components.buttonInset
import io.lukia.shared.presentation.components.EllipsisGlyph
import io.lukia.shared.presentation.components.FavoriteBadge
import io.lukia.shared.presentation.components.GroupCard
import io.lukia.shared.presentation.components.IconButtonVariant
import io.lukia.shared.presentation.components.LukiaButton
import io.lukia.shared.presentation.components.LukiaDivider
import io.lukia.shared.presentation.components.LukiaIconButton
import io.lukia.shared.presentation.components.SectionLabel
import io.lukia.shared.presentation.components.StatusBadge
import io.lukia.shared.presentation.components.UiStateContent
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Vertical room reserved so content starts clear of the floating back control. */
private val TopChromeClearance: Dp = 68.dp

/** Room past the last row, so the details do not end hard against the edge. */
private val DetailBottomClearance: Dp = 32.dp

/** How much of a blurb is shown before it is folded away behind "More". */
private const val CollapsedDescriptionLines = 5

/**
 * Inset from the card's edge to a row's text, and what the separators between
 * rows are inset by so they align to the text rather than to the container.
 */
private val InfoRowInset: Dp = Space.lg

@Composable
fun BookDetailScreen(
    location: String,
    onBack: () -> Unit,
    onRead: () -> Unit,
    viewModel: BookDetailViewModel = koinViewModel(parameters = { parametersOf(location) })
) {
    val uiState by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    BookDetailContent(
        uiState = uiState,
        onBack = onBack,
        onRead = onRead,
        onRetry = viewModel::retry,
        onToggleFavorite = viewModel::toggleFavorite,
        onRefresh = viewModel::refreshDocument,
        snackbarHostState = snackbarHostState,
        // Nothing left on this screen once the entry is gone, so leaving is part
        // of the action rather than something the user is left to do.
        onDelete = {
            viewModel.deleteDocument()
            onBack()
        }
    )
}

/**
 * Stateless half of the screen, so the layout can be composed in a test or a
 * preview without a ViewModel or a dependency graph behind it. Every screen in
 * the app splits this way.
 */
@Composable
fun BookDetailContent(
    uiState: UiState<BookDetails>,
    onBack: () -> Unit,
    onRead: () -> Unit,
    onRetry: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    /**
     * Defaulted so this screen still composes without a ViewModel behind it,
     * which is how it is tested and previewed.
     */
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val dimens = LocalLukiaDimens.current
    var menuExpanded by remember { mutableStateOf(false) }
    // The actions chrome is only meaningful once there is a book behind it.
    val currentDocument = uiState.dataOrNull?.document

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
            ) { details ->
                BookDetailBody(
                    details = details,
                    onRead = onRead,
                    onLongPress = { menuExpanded = true },
                    modifier = Modifier.fillMaxSize()
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
            LukiaIconButton(
                label = "Go back",
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = dimens.screenPadding, top = Space.lg),
                variant = IconButtonVariant.Glass
            ) { tint, glyph ->
                BackChevron(tint = tint, modifier = glyph)
            }

            // Mirrors the back control across the top of the screen. Always
            // visible rather than hover-revealed as on a card: there is one book
            // here and room to show its actions outright.
            if (currentDocument != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = dimens.screenPadding, top = Space.lg)
                ) {
                    LukiaIconButton(
                        label = "Book actions",
                        onClick = { menuExpanded = true },
                        variant = IconButtonVariant.Glass
                    ) { tint, glyph ->
                        EllipsisGlyph(tint = tint, modifier = glyph)
                    }

                    BookActionsMenu(
                        expanded = menuExpanded,
                        isFavorite = currentDocument.isFavorite,
                        onToggleFavorite = onToggleFavorite,
                        onRefresh = onRefresh,
                        onDelete = onDelete,
                        onDismissRequest = { menuExpanded = false }
                    )
                }
            }

            // By hand rather than through a Scaffold: this screen is full-bleed
            // on purpose, and wrapping it in one to carry a notice would put the
            // content back inside the insets it deliberately passes under.
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

/**
 * Everything there is to say about one book: its cover - which is also the way
 * into it - what it is called and who wrote it, what the publisher said about
 * it, and its particulars underneath.
 *
 * Shared, because this is shown two ways - as a screen of its own on a phone, and
 * as a panel over the library on anything wider - and the two must not drift into
 * saying different things about the same book. Only the chrome around it and the
 * room it leaves for that chrome differ, which is what [topClearance] and
 * [bottomClearance] are for.
 *
 * Sections earn their place: a book whose file carries no blurb has no About, and
 * one that named no publisher has no publisher row. A heading over an empty space
 * tells the reader only that the app expected something it did not get.
 */
@Composable
internal fun BookDetailBody(
    details: BookDetails,
    onRead: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    topClearance: Dp = TopChromeClearance,
    bottomClearance: Dp = DetailBottomClearance,
    coverWidth: Dp = Dp.Unspecified,
    /**
     * What the grouped details sit in. It has to contrast with whatever is behind
     * this body, and that differs: the screen lays it on the window background,
     * the panel on a raised surface, and one colour cannot be a step away from
     * both - in the dark theme especially, where the background is true black.
     */
    infoCardColor: Color = MaterialTheme.colorScheme.surface
) {
    val dimens = LocalLukiaDimens.current
    val document = details.document
    val metadata = document.metadata
    val title = document.bookTitle()

    // A screen can give the cover its full size, because the screen is the book.
    // A panel cannot: the cover would push everything worth reading below the
    // fold in any window that is not tall.
    val resolvedCoverWidth =
        if (coverWidth == Dp.Unspecified) dimens.detailCoverWidth else coverWidth

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(topClearance))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = dimens.maxDetailWidth)
                .fillMaxWidth()
                .padding(horizontal = dimens.screenPadding)
        ) {
            Box {
                ReadableCover(
                    coverImage = document.coverImage,
                    title = title,
                    progress = details.percentRead?.let { it / 100f },
                    enabled = document.exists,
                    onRead = onRead,
                    onLongPress = onLongPress,
                    width = resolvedCoverWidth
                )

                FavoriteBadge(
                    visible = document.isFavorite,
                    size = 28.dp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(Space.md)
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = dimens.sectionSpacing)
            )

            // The accent colour, as a book's byline is set everywhere it is set:
            // it is the second thing anyone reads and the one that decides
            // whether this is the book they meant.
            metadata?.authorLine()?.let { authors ->
                Text(
                    text = authors,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Space.xs)
                )
            }

            // What pressing the cover would do, and how far in the reader
            // already is - the caption the cover cannot carry itself.
            Text(
                text = if (document.exists) {
                    "${details.readActionLabel()} · ${details.progressLabel()}"
                } else {
                    details.progressLabel()
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Space.sm)
            )

            if (!document.exists) {
                StatusBadge(
                    text = "This file is no longer available",
                    modifier = Modifier.padding(top = Space.lg)
                )
            }

            metadata?.description?.let { description ->
                DescriptionSection(
                    description = description,
                    modifier = Modifier.padding(top = dimens.sectionSpacing)
                )
            }

            InformationCard(
                details = details,
                color = infoCardColor,
                modifier = Modifier.padding(top = dimens.sectionSpacing)
            )

            metadata?.subjects?.takeIf { it.isNotEmpty() }?.let { subjects ->
                SubjectSection(
                    subjects = subjects,
                    modifier = Modifier.padding(top = dimens.sectionSpacing)
                )
            }
        }

        Spacer(modifier = Modifier.height(bottomClearance))
    }
}

/**
 * The publisher's blurb, folded to a few lines with a way to see the rest.
 *
 * The fold is offered only when there is something folded away. Whether the text
 * overflows is something only the layout knows, so it is asked rather than
 * guessed at from a character count - which would put a "More" under a blurb
 * that was already showing in full whenever the window was wide.
 */
@Composable
private fun DescriptionSection(
    description: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember(description) { mutableStateOf(false) }
    var overflows by remember(description) { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel(text = "About")

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            // The blurb is the one paragraph on this screen anyone reads at
            // length, so it gets full-strength body colour rather than the muted
            // one the particulars beneath it wear.
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (expanded) Int.MAX_VALUE else CollapsedDescriptionLines,
            overflow = TextOverflow.Ellipsis,
            // Only meaningful while folded: expanded text never overflows, and
            // recording that would take the control away mid-read.
            onTextLayout = { layout ->
                if (!expanded) overflows = layout.hasVisualOverflow
            },
            modifier = Modifier.padding(top = Space.sm)
        )

        if (overflows || expanded) {
            LukiaButton(
                label = if (expanded) "Less" else "More",
                onClick = { expanded = !expanded },
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Compact,
                // Pulled back by its own inset so the label starts on the same
                // line as the paragraph it belongs to; a ghost button has no
                // container to make that padding look deliberate.
                modifier = Modifier.offset(x = -buttonInset(ButtonSize.Compact))
            )
        }
    }
}

/**
 * The book's particulars, in the style of an iOS grouped list.
 *
 * Built from whatever there is rather than from a fixed set of rows, so a book
 * that named a publisher gets a publisher row and one that did not is not given
 * an empty one. Where the file lives is kept out of the group and set below it:
 * it is the only value long enough to need two lines, and the only one that is
 * about the file rather than about the book.
 */
@Composable
private fun InformationCard(
    details: BookDetails,
    color: Color,
    modifier: Modifier = Modifier
) {
    val document = details.document
    val metadata = document.metadata

    val rows = buildList {
        metadata?.publisher?.let { add("Publisher" to it) }
        metadata?.publishedYear()?.let { add("Published" to it) }
        metadata?.languageName()?.let { add("Language" to it) }
        details.lengthLabel()?.let { add("Length" to it) }
        add("Format" to document.displayType())
        add("Size" to document.formattedSize())
        metadata?.identifier?.let { add("ISBN" to it) }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel(text = "Information")

        GroupCard(
            color = color,
            modifier = Modifier.padding(top = Space.sm)
        ) {
            Column {
                rows.forEachIndexed { index, (label, value) ->
                    if (index > 0) LukiaDivider(inset = InfoRowInset)

                    InfoRow(label = label, value = value)
                }
            }
        }

        Text(
            text = document.document.location,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Space.xs, top = Space.sm)
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = InfoRowInset, vertical = Space.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            textAlign = TextAlign.End,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = InfoRowInset)
        )
    }
}

/**
 * What the book filed itself under, as tags.
 *
 * Wrapped rather than scrolled sideways: a book can carry a dozen BISAC headings
 * and a row that ran off the edge would hide most of them behind a gesture
 * nothing on screen suggests.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubjectSection(
    subjects: List<String>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel(text = "Subjects")

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
            modifier = Modifier.padding(top = Space.sm)
        ) {
            subjects.forEach { subject ->
                StatusBadge(text = subject, tone = BadgeTone.Neutral)
            }
        }
    }
}
