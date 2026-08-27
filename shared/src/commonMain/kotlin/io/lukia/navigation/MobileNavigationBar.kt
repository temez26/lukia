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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.components.scaleOnPress
import io.lukia.shared.presentation.theme.LukiaMotion
import io.lukia.shared.presentation.theme.MinTouchTarget
import io.lukia.shared.presentation.theme.Space

/** The bar itself, above whatever the system reserves below it. */
private val BarContentHeight: Dp = 52.dp

/** The mark over each label. Smaller than a button's, since it repeats three times. */
private val TabIconSize: Dp = 24.dp

/**
 * How much room the bar takes at the bottom of the screen, system inset
 * included.
 *
 * The tab screens read this to know where their content has to stop. The bar is
 * drawn over them rather than beside them - it is one strip shared by three
 * screens, and re-laying the whole screen out on every tab change to make room
 * for something that never moves would be work for nothing.
 */
@Composable
internal fun mobileNavBarHeight(): Dp =
    BarContentHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/**
 * The bar along the bottom of the mobile app.
 *
 * Built by hand rather than from Material's `NavigationBar` for the reason the
 * rest of this app's chrome is: Material draws a pill-shaped indicator that
 * slides behind the selected mark, which belongs to a different design language
 * than the drawn, Apple-proportioned controls every other screen here wears.
 * What marks the selected tab instead is colour and weight, which is what a tab
 * bar has always used.
 *
 * Labels are always shown. A bar of three unlabelled glyphs asks the reader to
 * learn what a house and a stack of books mean in this particular app, and the
 * room saved by dropping two words is room nothing else wanted.
 */
@Composable
internal fun MobileNavigationBar(
    selected: MobileTab,
    onSelect: (MobileTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val barColor = MaterialTheme.colorScheme.background
    val hairlineColor = MaterialTheme.colorScheme.outlineVariant

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

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.xxs),
        modifier = Modifier
            .weight(1f)
            .height(MinTouchTarget + Space.md)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .scaleOnPress(interactionSource, pressedScale = 0.92f)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(top = Space.sm)
            // One node for the pair, so a screen reader announces "Library,
            // tab, selected" rather than reading a glyph and a word separately.
            // The role is what supplies the middle of that: this is a drawn Row
            // rather than Material's `NavigationBarItem`, so nothing else says
            // the bar is a set of tabs rather than three unrelated controls.
            .semantics(mergeDescendants = true) {
                contentDescription = tab.label
                this.selected = selected
            },
    ) {
        tab.icon(tint, Modifier.size(TabIconSize))

        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            // The selected tab's name thickens along with its colour. Colour
            // alone is not something everybody can read.
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = tint,
        )
    }
}
