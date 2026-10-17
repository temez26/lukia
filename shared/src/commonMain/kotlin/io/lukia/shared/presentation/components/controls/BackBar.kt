package io.lukia.shared.presentation.components.controls

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import io.lukia.resources.Res
import io.lukia.resources.action_go_back
import io.lukia.shared.presentation.components.icons.BackChevron
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/**
 * The top of a screen pushed over the app: the way back, the screen's name when
 * it is not already set large in the content, and the screen's own actions at
 * the far end.
 *
 * Held still above the content rather than floating over it. Floating, the
 * headings of a scrolled page slid under the back button and stayed readable
 * beside it. One bar for every such screen also puts the way back in the same
 * place on each, so a thumb that has learned it on one finds it on the next.
 */
@Composable
fun BackBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val dimens = LocalLukiaDimens.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = dimens.screenPadding, end = dimens.screenPadding, top = Space.lg, bottom = Space.sm),
    ) {
        BackButton(onBack = onBack)

        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Space.lg)
                    .semantics { heading() },
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        actions()
    }
}

/** The way back itself, as [BackBar] sets it. */
@Composable
fun BackButton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LukiaIconButton(
        label = stringResource(Res.string.action_go_back),
        onClick = onBack,
        modifier = modifier,
        variant = IconButtonVariant.Glass,
    ) { tint, glyph ->
        BackChevron(tint = tint, modifier = glyph)
    }
}
