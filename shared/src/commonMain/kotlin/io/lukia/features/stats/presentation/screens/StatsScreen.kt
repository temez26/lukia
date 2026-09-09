package io.lukia.features.stats.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.lukia.core.presentation.UiState
import io.lukia.features.stats.domain.ReadingSummary
import io.lukia.features.stats.presentation.StatsViewModel
import io.lukia.resources.Res
import io.lukia.resources.action_go_back
import io.lukia.shared.presentation.components.controls.IconButtonVariant
import io.lukia.shared.presentation.components.controls.LukiaIconButton
import io.lukia.shared.presentation.components.icons.BackChevron
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * What the reading adds up to: the week so far, everything ever counted, what is
 * on the shelf, and what has been open lately.
 *
 * [onOpenBook] takes a location rather than a document, because that is all this
 * screen holds - it lists reading positions with the library's entry beside
 * each, and opening a book is the library's own route by location.
 */
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
    viewModel: StatsViewModel = koinViewModel(),
) {
    val summary by viewModel.summary.collectAsState()

    // Counted again each time the page is returned to, which is what a reader
    // coming back from a book expects: the pages and the minutes they have just
    // spent are the whole point of looking.
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    StatsContent(
        summary = summary,
        onBack = onBack,
        actions = remember(viewModel, onOpenBook) {
            StatsActions(onOpenBook = onOpenBook, onRetry = viewModel::refresh)
        },
    )
}

/**
 * Full-bleed like the settings and detail screens, so the page passes under the
 * floating back control rather than starting below a bar.
 */
@Composable
internal fun StatsContent(
    summary: UiState<ReadingSummary>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: StatsActions = StatsActions(),
) {
    val dimens = LocalLukiaDimens.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // The background stays outside the inset so colour still reaches the
        // screen edges; the content inside it does not. Resolves to zero on
        // desktop.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            StatsBody(
                summary = summary,
                actions = actions,
                modifier = Modifier.fillMaxSize(),
            )

            LukiaIconButton(
                label = stringResource(Res.string.action_go_back),
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = dimens.screenPadding, top = Space.lg),
                variant = IconButtonVariant.Glass,
            ) { tint, glyph ->
                BackChevron(tint = tint, modifier = glyph)
            }
        }
    }
}
