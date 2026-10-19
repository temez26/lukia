package io.lukia.features.library.presentation.components.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import io.lukia.shared.presentation.theme.LocalIconButtonShape
import io.lukia.shared.presentation.theme.LukiaAlpha

/**
 * A wash of the accent behind one of a header's buttons, in the buttons' own
 * shape. The statistics button arrives as a slot from the shell, so a header
 * dresses it rather than asking for a different one.
 *
 * Home's header and the library's bar both wear it, so the same two buttons
 * look the same on either tab.
 */
@Composable
internal fun TonalPlate(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = LocalIconButtonShape.current

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = LukiaAlpha.Tonal)),
    ) {
        content()
    }
}
