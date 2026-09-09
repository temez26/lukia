package io.lukia.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.components.controls.scaleOnPress
import io.lukia.shared.presentation.theme.LukiaMotion
import io.lukia.shared.presentation.theme.MinTouchTarget
import io.lukia.shared.presentation.theme.Space
import io.lukia.shared.presentation.theme.isShortWindow
import org.jetbrains.compose.resources.stringResource

/** The bar itself, above whatever the system reserves below it. */
private val BarContentHeight: Dp = 52.dp

/**
 * What the bar shrinks to when the window is short - see [isShortWindow].
 *
 * Still a full touch target, and no less. What is saved is the padding around
 * the mark and the row the label had to itself, both of which are affordable
 * only on a screen that is not already out of height.
 */
private val ShortBarContentHeight: Dp = MinTouchTarget

/** The mark over each label. Smaller than a button's, since it repeats three times. */
private val TabIconSize: Dp = 24.dp

/** ...and smaller again beside its label rather than over it. */
private val InlineTabIconSize: Dp = 20.dp

/**
 * How much room the bar takes at the bottom of the screen, system inset
 * included.
 *
 * The tab screens read this to know where their content has to stop. The bar is
 * drawn over them rather than beside them - it is one strip shared by three
 * screens, and re-laying the whole screen out on every tab change to make room
 * for something that never moves would be work for nothing.
 *
 * A short window gets the shorter bar, and every screen behind it is handed the
 * smaller inset without knowing that anything changed.
 */
@Composable
internal fun mobileNavBarHeight(): Dp =
    barContentHeight() + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

@Composable
private fun barContentHeight(): Dp =
    if (isShortWindow) ShortBarContentHeight else BarContentHeight

/**
 * The bar along the bottom of the mobile app.
 *
 * Built by hand rather than from Material's `NavigationBar`, for the reason the
 * rest of this app's chrome is: Material slides a pill-shaped indicator behind
 * the selected mark, which belongs to a different design language than the
 * drawn, Apple-proportioned controls elsewhere here. Colour and weight mark the
 * selected tab instead, as a tab bar always has.
 *
 * Labels are always shown: three unlabelled glyphs ask the reader to learn what
 * a house and a stack of books mean in this app, and the room saved by dropping
 * two words is room nothing else wanted.
 *
 * A short window changes where the label sits, not whether it is there. Stacked
 * under the mark a tab costs 52dp of a 390dp-tall landscape phone; beside it the
 * same tab costs 44dp and stays a full touch target - the shape both platforms
 * give a tab bar when the screen is turned.
 */
@Composable
internal fun MobileNavigationBar(
    selected: MobileTab,
    onSelect: (MobileTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val barColor = MaterialTheme.colorScheme.background
    val hairlineColor = MaterialTheme.colorScheme.outlineVariant
    val inline = isShortWindow

    Row(
        verticalAlignment = Alignment.Top,
        modifier = modifier
            .fillMaxWidth()
            .height(mobileNavBarHeight())
            // Opaque, and for the same reason the library's own bar is: the
            // content passing under it is book covers, and the app's hand-drawn
            // glass is not a real backdrop blur, so artwork behind it would
            // smear rather than frost.
            .drawBehind {
                drawRect(color = barColor)
                drawLine(
                    color = hairlineColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1f,
                )
            },
    ) {
        MobileTab.entries.forEach { tab ->
            TabItem(
                tab = tab,
                selected = tab == selected,
                inline = inline,
                onClick = { onSelect(tab) },
            )
        }
    }
}

/**
 * One tab: its mark, its name, and whether it is the one you are looking at.
 *
 * The whole item is the target rather than the glyph inside it, so the bar has
 * no dead ground between its tabs - a thumb aimed anywhere in the third of the
 * screen a tab occupies lands on it.
 */
@Composable
private fun RowScope.TabItem(
    tab: MobileTab,
    selected: Boolean,
    inline: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    val tint by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = LukiaMotion.standard(),
        label = "tabTint",
    )

    // Read before the modifier chain: `semantics` is not a composable
    // scope, and the same word serves the spoken name and the visible label.
    val name = stringResource(tab.label)

    val target = Modifier
        .weight(1f)
        .height(if (inline) MinTouchTarget else MinTouchTarget + Space.md)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            role = Role.Tab,
            onClick = onClick,
        )
        .scaleOnPress(interactionSource, pressedScale = 0.92f)
        .pointerHoverIcon(PointerIcon.Hand)
        // One node for the pair, so a screen reader announces "Library,
        // tab, selected" rather than reading a glyph and a word separately.
        // The role is what supplies the middle of that: this is a drawn Row
        // rather than Material's `NavigationBarItem`, so nothing else says
        // the bar is a set of tabs rather than three unrelated controls.
        .semantics(mergeDescendants = true) {
            contentDescription = name
            this.selected = selected
        }

    if (inline) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.sm, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = target,
        ) {
            tab.icon(tint, Modifier.size(InlineTabIconSize))

            TabLabel(name = name, selected = selected, tint = tint)
        }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.xxs),
            modifier = target.padding(top = Space.sm),
        ) {
            tab.icon(tint, Modifier.size(TabIconSize))

            TabLabel(name = name, selected = selected, tint = tint)
        }
    }
}

@Composable
private fun TabLabel(name: String, selected: Boolean, tint: Color) {
    Text(
        text = name,
        style = MaterialTheme.typography.labelSmall,
        // The selected tab's name thickens along with its colour. Colour
        // alone is not something everybody can read.
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = tint,
    )
}
