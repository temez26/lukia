package io.lukia.features.library.presentation.components.home

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.resources.Res
import io.lukia.resources.home_add_books
import io.lukia.resources.library_settings
import io.lukia.shared.presentation.components.controls.pressable
import io.lukia.shared.presentation.components.icons.GearGlyph
import io.lukia.shared.presentation.components.icons.PlusGlyph
import io.lukia.shared.presentation.components.surface.GroupCard
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

private val ActionGlyphSize: Dp = 18.dp

/**
 * The card under the book being read: a glance at the week's reading, supplied
 * by the shell, with Home's own two actions along its foot - adding books and
 * the settings. They were the header's menu; on the page they are one press
 * away rather than two, and the header is left to the title.
 */
@Composable
internal fun HomeGlance(
    onAddBooks: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    stats: @Composable () -> Unit = {},
) {
    GroupCard(modifier = modifier) {
        Column {
            stats()

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                GlanceAction(
                    label = stringResource(Res.string.home_add_books),
                    onClick = onAddBooks,
                    glyph = { tint, glyphModifier -> PlusGlyph(tint = tint, modifier = glyphModifier) },
                )

                VerticalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.fillMaxHeight(),
                )

                GlanceAction(
                    label = stringResource(Res.string.library_settings),
                    onClick = onSettings,
                    glyph = { tint, glyphModifier -> GearGlyph(tint = tint, modifier = glyphModifier) },
                )
            }
        }
    }
}

@Composable
private fun RowScope.GlanceAction(
    label: String,
    onClick: () -> Unit,
    glyph: @Composable (Color, Modifier) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Space.sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .weight(1f)
            .pressable(
                interactionSource = remember { MutableInteractionSource() },
                shape = RectangleShape,
                onClickLabel = label,
                onClick = onClick,
            )
            .padding(vertical = Space.md),
    ) {
        glyph(MaterialTheme.colorScheme.primary, Modifier.size(ActionGlyphSize))

        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}
