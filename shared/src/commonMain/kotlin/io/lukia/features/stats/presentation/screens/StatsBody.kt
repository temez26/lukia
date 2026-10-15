package io.lukia.features.stats.presentation.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.displayMessage
import io.lukia.core.presentation.displayTitle
import io.lukia.features.stats.domain.ReadingSummary
import io.lukia.resources.Res
import io.lukia.resources.action_try_again
import io.lukia.resources.stats_footnote
import io.lukia.resources.stats_footnote_synced
import io.lukia.resources.stats_title
import io.lukia.shared.presentation.components.state.ErrorState
import io.lukia.shared.presentation.components.state.LoadingState
import io.lukia.shared.presentation.theme.Clearance
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import io.lukia.shared.presentation.theme.softFadeOut
import io.lukia.shared.presentation.theme.stateSwapIn
import org.jetbrains.compose.resources.stringResource

/**
 * Room held while the figures are being counted, so the page arrives at roughly
 * the height it will settle at rather than growing under the reader.
 */
private val StatsPlaceholderHeight: Dp = 260.dp

/**
 * The statistics themselves, without the chrome around them.
 *
 * Written as a body of its own rather than inside the screen for the reason
 * `SettingsBody` is: a wide window has room to lay this over the library instead
 * of replacing it, and the day that is worth doing this is the part that moves.
 */
@Composable
internal fun StatsBody(
    summary: UiState<ReadingSummary>,
    actions: StatsActions,
    modifier: Modifier = Modifier,
    /**
     * What a group sits in, for the same reason `SettingsBody` takes one: this
     * has to contrast with whatever is behind it, and a screen's background and
     * a panel's surface are not the same colour.
     */
    groupColor: Color = MaterialTheme.colorScheme.surface,
    syncedWithAccount: Boolean = false,
) {
    val dimens = LocalLukiaDimens.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(Clearance.topChrome))

        Column(
            modifier = Modifier
                .widthIn(max = dimens.maxDetailWidth)
                .fillMaxWidth()
                .padding(horizontal = dimens.screenPadding),
        ) {
            StatsTitle(syncedWithAccount)

            CountedContent(
                summary = summary,
                actions = actions,
                groupColor = groupColor,
                modifier = Modifier.padding(top = dimens.sectionSpacing),
            )
        }

        Spacer(modifier = Modifier.height(Clearance.pageBottom))
    }
}

/**
 * Everything below the title, which is everything that has to be counted first.
 *
 * One wait for the whole page rather than one per card. Every figure comes from
 * a single read of the library and its sittings, so three sections each deciding
 * for themselves whether they had anything yet appeared at three different
 * moments - the two highest shoving the rest of the page down as they did, in
 * the middle of the screen still sliding in over the shelf.
 *
 * The swap is the app's own - see `UiStateContent`, which does this for a screen
 * where this does it for a section of one. Keyed on which state it is rather
 * than on the state itself, so a second count landing with new figures does not
 * dissolve the page into itself.
 */
@Composable
private fun CountedContent(
    summary: UiState<ReadingSummary>,
    actions: StatsActions,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = summary,
        modifier = modifier.fillMaxWidth(),
        // `using null` keeps AnimatedContent from animating the container's
        // bounds as well, which slides the content sideways mid-transition.
        transitionSpec = { (stateSwapIn() togetherWith softFadeOut()) using null },
        contentKey = { state ->
            when (state) {
                is UiState.Loading -> "loading"
                is UiState.Failure -> "failure"
                is UiState.Success -> "success"
            }
        },
        label = "statsContent",
    ) { state ->
        when (state) {
            is UiState.Loading -> LoadingState(
                modifier = Modifier.fillMaxWidth().height(StatsPlaceholderHeight),
            )

            is UiState.Failure -> ErrorState(
                title = state.error.displayTitle(),
                message = state.error.displayMessage(),
                actionLabel = stringResource(Res.string.action_try_again),
                onAction = actions.onRetry,
                modifier = Modifier.fillMaxWidth(),
            )

            is UiState.Success -> StatsSections(
                summary = state.data,
                onOpenBook = actions.onOpenBook,
                groupColor = groupColor,
            )
        }
    }
}

/**
 * The counted figures, in the order they are worth reading.
 *
 * From the smallest span of time to the largest and then out to what is on the
 * shelf: today and this week, then everything ever counted, then the library,
 * then the books last open. Somebody opening this has just put a book down, so
 * what they did today is the first thing on the page and the size of their
 * collection is the last.
 */
@Composable
private fun StatsSections(
    summary: ReadingSummary,
    onOpenBook: (String) -> Unit,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalLukiaDimens.current

    Column(modifier = modifier.fillMaxWidth()) {
        ThisWeekSection(habits = summary.habits, groupColor = groupColor)

        AllTimeSection(
            habits = summary.habits,
            groupColor = groupColor,
            modifier = Modifier.padding(top = dimens.sectionSpacing),
        )

        LibrarySection(
            stats = summary.stats,
            groupColor = groupColor,
            modifier = Modifier.padding(top = dimens.sectionSpacing),
        )

        ActivitySection(
            activity = summary.recent,
            onOpenBook = onOpenBook,
            groupColor = groupColor,
            modifier = Modifier.padding(top = dimens.sectionSpacing),
        )
    }
}

/**
 * What the page is, in a large title set against the start edge.
 *
 * Above the count rather than inside it, so a library that cannot be read still
 * leaves the reader on a page that says what it was going to be.
 *
 * Aligned with the groups under it rather than centred. There is no person at
 * the top of this screen any more, so there is nothing that wants a centre line
 * - and a heading that starts where every card under it starts is what makes the
 * page read as one column.
 */
@Composable
private fun StatsTitle(syncedWithAccount: Boolean, modifier: Modifier = Modifier) {
    val footnote = if (syncedWithAccount) Res.string.stats_footnote_synced else Res.string.stats_footnote

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.stats_title),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Text(
            text = stringResource(footnote),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.xs),
        )
    }
}

/**
 * Everything the statistics page can do.
 *
 * Gathered rather than passed one callback at a time, matching the rest of the
 * app's screens: sections take the one they need out of it, and adding a section
 * that needs a third does not change every signature between here and it.
 */
internal data class StatsActions(
    val onOpenBook: (String) -> Unit = {},
    val onRetry: () -> Unit = {},
)
