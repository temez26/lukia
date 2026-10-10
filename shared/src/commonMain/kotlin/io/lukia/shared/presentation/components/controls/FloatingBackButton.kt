package io.lukia.shared.presentation.components.controls

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.lukia.resources.Res
import io.lukia.resources.action_go_back
import io.lukia.shared.presentation.components.icons.BackChevron
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/**
 * The way back out of a screen that fills the window, floating over its top
 * corner while the content scrolls underneath.
 *
 * One of these rather than one per screen, so that every such screen puts it in
 * the same place and a thumb that has learned where it is on one finds it on
 * the next.
 */
@Composable
fun BoxScope.FloatingBackButton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LukiaIconButton(
        label = stringResource(Res.string.action_go_back),
        onClick = onBack,
        modifier = modifier
            .align(Alignment.TopStart)
            .padding(start = LocalLukiaDimens.current.screenPadding, top = Space.lg),
        variant = IconButtonVariant.Glass,
    ) { tint, glyph ->
        BackChevron(tint = tint, modifier = glyph)
    }
}
