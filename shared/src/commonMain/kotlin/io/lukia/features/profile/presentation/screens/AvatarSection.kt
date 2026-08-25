package io.lukia.features.profile.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.profile.domain.ProfileAvatar
import io.lukia.features.profile.domain.ProfileSymbol
import io.lukia.features.profile.domain.ProfileTint
import io.lukia.features.profile.presentation.components.ProfileFace
import io.lukia.features.profile.presentation.components.ProfileSymbolGlyph
import io.lukia.features.profile.presentation.components.tintColors
import io.lukia.shared.presentation.components.ButtonSize
import io.lukia.shared.presentation.components.ButtonVariant
import io.lukia.shared.presentation.components.GroupSection
import io.lukia.shared.presentation.components.LukiaButton
import io.lukia.shared.presentation.theme.HairlineWidth
import io.lukia.shared.presentation.theme.LukiaAlpha
import io.lukia.shared.presentation.theme.Space

/**
 * The choosable discs: as large as fits seven of them across the narrowest
 * window this app runs in, and no smaller than a mark can be told apart at.
 */
private val ChoiceSize: Dp = 40.dp

/** The ring drawn around whichever choice is on. */
private val SelectionRing: Dp = 2.dp

/**
 * What the reader wears, and everything they can change about it.
 *
 * Two rows and a button, in the order somebody works through them: the mark
 * first, because it is what the disc actually shows; its colour second, which
 * only means anything once there is something to colour; and a picture of their
 * own last, because it replaces both and most readers will never want it.
 *
 * The marks are shown as the discs they will become rather than as a list of
 * names. A row of small circles is the choice made visible - "Feather" is a word
 * that has to be imagined, while a feather on a green disc is the answer.
 */
@Composable
internal fun AvatarSection(
    face: ProfileFace,
    actions: AvatarActions,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    GroupSection(
        title = "Your picture",
        color = groupColor,
        footnote = "A picture stays on this device, like everything else here.",
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.xl)) {
            MarkChoices(face = face, onChoose = actions.onAvatar)

            TintChoices(selected = face.tint, onChoose = actions.onTint)

            PictureControls(usingPicture = face.picture != null, actions = actions)
        }
    }
}

/**
 * What the app itself can put on the disc: the reader's initials, and the six
 * marks that ship with it.
 *
 * The monogram leads because it is what a new profile already wears, so the row
 * starts at where the reader is rather than at an alternative to it.
 */
@Composable
private fun MarkChoices(
    face: ProfileFace,
    onChoose: (ProfileAvatar) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = tintColors(face.tint)

    Row(
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        AvatarChoice(
            fill = colors.fill,
            selected = face.avatar == ProfileAvatar.Monogram,
            label = "Your initials",
            onSelect = { onChoose(ProfileAvatar.Monogram) },
        ) {
            Monogram(initials = face.initials, color = colors.content)
        }

        ProfileSymbol.entries.forEach { symbol ->
            AvatarChoice(
                fill = colors.fill,
                selected = (face.avatar as? ProfileAvatar.Symbol)?.symbol == symbol,
                label = symbol.title,
                onSelect = { onChoose(ProfileAvatar.Symbol(symbol)) },
            ) {
                ProfileSymbolGlyph(
                    symbol = symbol,
                    tint = colors.content,
                    modifier = Modifier.size(ChoiceSize),
                )
            }
        }
    }
}

/** The initials on a choice disc, sized for it rather than for the page. */
@Composable
private fun Monogram(initials: String, color: Color) {
    Text(
        text = initials,
        style = MaterialTheme.typography.labelLarge,
        color = color,
    )
}

/**
 * The colours a disc can be.
 *
 * Swatches rather than names, and the same reasoning as the accent swatches in
 * Settings: a colour is the one setting that can show itself. Each carries its
 * name for anything that cannot see it, which is also what makes them reachable
 * from a test.
 */
@Composable
private fun TintChoices(
    selected: ProfileTint,
    onChoose: (ProfileTint) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        ProfileTint.entries.forEach { tint ->
            val colors = tintColors(tint)

            AvatarChoice(
                fill = colors.fill,
                selected = tint == selected,
                label = tint.title,
                size = ChoiceSize * 0.7f,
                onSelect = { onChoose(tint) },
                content = {},
            )
        }
    }
}

/**
 * One disc to press, marked when it is the one in use.
 *
 * The ring sits outside the disc rather than on it, so choosing a mark does not
 * change the size of the thing being chosen - and so the ring is legible against
 * a swatch of its own colour.
 */
@Composable
private fun AvatarChoice(
    fill: Color,
    selected: Boolean,
    label: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = ChoiceSize,
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(fill)
            .then(
                if (selected) {
                    Modifier.border(
                        SelectionRing,
                        MaterialTheme.colorScheme.onBackground,
                        CircleShape,
                    )
                } else {
                    Modifier.border(
                        HairlineWidth,
                        MaterialTheme.colorScheme.onBackground.copy(alpha = LukiaAlpha.Hover),
                        CircleShape,
                    )
                },
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics { contentDescription = label },
    ) {
        content()
    }
}

/**
 * Choosing a picture, and taking it off again.
 *
 * The way back is offered only when there is a picture to remove: a disabled
 * "Remove" beside a disc that has never had one is a control explaining itself
 * for nothing.
 */
@Composable
private fun PictureControls(
    usingPicture: Boolean,
    actions: AvatarActions,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        modifier = modifier.fillMaxWidth(),
    ) {
        LukiaButton(
            label = if (usingPicture) "Change picture" else "Choose a picture",
            onClick = actions.onChoosePicture,
            variant = ButtonVariant.Secondary,
            size = ButtonSize.Compact,
        )

        if (usingPicture) {
            LukiaButton(
                label = "Remove",
                onClick = actions.onRemovePicture,
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Compact,
            )
        }
    }
}

/**
 * Everything this section can do, gathered so the profile page passes one thing
 * rather than four callbacks it does nothing with itself.
 */
data class AvatarActions(
    val onAvatar: (ProfileAvatar) -> Unit = {},
    val onTint: (ProfileTint) -> Unit = {},
    val onChoosePicture: () -> Unit = {},
    val onRemovePicture: () -> Unit = {},
)
