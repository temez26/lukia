package io.lukia.features.stats.presentation.screens

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
import io.lukia.features.stats.domain.ReadingSummary
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space

/** Vertical room reserved so content starts clear of the floating back control. */
private val TopChromeClearance: Dp = 68.dp

/** Room past the last group, so the page does not end hard against the edge. */
private val StatsBottomClearance: Dp = 32.dp

/**
 * The statistics themselves, without the chrome around them.
 *
 * Written as a body of its own rather than inside the screen for the reason
 * `SettingsBody` is: a wide window has room to lay this over the library instead
 * of replacing it, and the day that is worth doing this is the part that moves.
 *
 * The order runs from the smallest span of time to the largest and then out to
 * what is on the shelf: today and this week, then everything ever counted, then
 * the library, then the books last open. Somebody opening this has just put a
 * book down, so what they did today is the first thing on the page and the size
 * of their collection is the last.
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
) {
    val dimens = LocalLukiaDimens.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(TopChromeClearance))

        Column(
            modifier = Modifier
                .widthIn(max = dimens.maxDetailWidth)
                .fillMaxWidth()
                .padding(horizontal = dimens.screenPadding),
        ) {
            StatsTitle()

            ThisWeekSection(
                summary = summary,
                groupColor = groupColor,
                modifier = Modifier.padding(top = dimens.sectionSpacing),
            )

            AllTimeSection(
                summary = summary,
                groupColor = groupColor,
                modifier = Modifier.padding(top = dimens.sectionSpacing),
            )

            LibrarySection(
                summary = summary,
                onRetry = actions.onRetry,
                groupColor = groupColor,
                modifier = Modifier.padding(top = dimens.sectionSpacing),
            )

            ActivitySection(
                summary = summary,
                onOpenBook = actions.onOpenBook,
                groupColor = groupColor,
                modifier = Modifier.padding(top = dimens.sectionSpacing),
            )
        }

        Spacer(modifier = Modifier.height(StatsBottomClearance))
    }
}

/**
 * What the page is, in a large title set against the start edge.
 *
 * Aligned with the groups under it rather than centred. There is no person at
 * the top of this screen any more, so there is nothing that wants a centre line
 * - and a heading that starts where every card under it starts is what makes the
 * page read as one column.
 */
@Composable
private fun StatsTitle(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Reading",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Text(
            text = "Counted on this device, and kept here.",
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
