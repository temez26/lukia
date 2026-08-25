package io.lukia.features.profile.presentation.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.presentation.UiState
import io.lukia.features.profile.domain.ReadingSummary
import io.lukia.features.profile.domain.UserProfile
import io.lukia.features.profile.presentation.components.ProfileHeader
import io.lukia.shared.presentation.theme.LocalLukiaDimens

/** Vertical room reserved so content starts clear of the floating back control. */
private val TopChromeClearance: Dp = 68.dp

/** Room past the last group, so the page does not end hard against the edge. */
private val ProfileBottomClearance: Dp = 32.dp

/**
 * The profile itself, without the chrome around it.
 *
 * Written as a body of its own rather than inside the screen for the reason
 * `SettingsBody` is: a wide window has room to lay this over the library instead
 * of replacing it, and the day that is worth doing this is the part that moves.
 *
 * The order is who, then how much, then what lately, then the two things that
 * can be changed. Everything a reader came to look at is above everything they
 * came to edit, which is why the name sits below its own statistics rather than
 * beside the avatar that shows it.
 */
@Composable
internal fun ProfileBody(
    profile: UserProfile,
    summary: UiState<ReadingSummary>,
    onRename: (String) -> Unit,
    onOpenBook: (String) -> Unit,
    onRetry: () -> Unit,
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
            ProfileHeader(profile = profile)

            ReadingSection(
                summary = summary,
                onRetry = onRetry,
                groupColor = groupColor,
                modifier = Modifier.padding(top = dimens.sectionSpacing),
            )

            ActivitySection(
                summary = summary,
                onOpenBook = onOpenBook,
                groupColor = groupColor,
                modifier = Modifier.padding(top = dimens.sectionSpacing),
            )

            NameSection(
                profile = profile,
                onRename = onRename,
                groupColor = groupColor,
                modifier = Modifier.padding(top = dimens.sectionSpacing),
            )

            AccountSection(
                account = profile.account,
                groupColor = groupColor,
                modifier = Modifier.padding(top = dimens.sectionSpacing),
            )
        }

        Spacer(modifier = Modifier.height(ProfileBottomClearance))
    }
}
