package io.lukia.features.settings.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.LukiaVersion
import io.lukia.core.presentation.displayName
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.presentation.displayName
import io.lukia.features.settings.domain.AppSettings
import io.lukia.features.translate.domain.settings.TranslationSettings
import io.lukia.resources.Res
import io.lukia.resources.settings_open_area
import io.lukia.resources.settings_reading_summary
import io.lukia.resources.settings_title
import io.lukia.resources.state_off
import io.lukia.resources.state_on
import io.lukia.shared.presentation.components.controls.pressable
import io.lukia.shared.presentation.components.icons.AppearanceGlyph
import io.lukia.shared.presentation.components.icons.CloudGlyph
import io.lukia.shared.presentation.components.icons.ForwardChevron
import io.lukia.shared.presentation.components.icons.InfoGlyph
import io.lukia.shared.presentation.components.icons.OpenBookGlyph
import io.lukia.shared.presentation.components.icons.SparkleGlyph
import io.lukia.shared.presentation.components.icons.TranslateGlyph
import io.lukia.shared.presentation.components.surface.GroupCard
import io.lukia.shared.presentation.components.surface.LukiaDivider
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.LukiaAlpha
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

private val TileSize: Dp = 30.dp
private val TileGlyphSize: Dp = 18.dp
private val AvatarSize: Dp = 44.dp
private val ChevronSize: Dp = 14.dp
private val RowMinHeight: Dp = 52.dp

/**
 * Past this, a row's value gives way to its name. The name is what the row is
 * found by; the value is a reminder, and an ellipsis does not lose it.
 */
private val ValueMaxWidth: Dp = 150.dp

/** A glyph drawn in a tint at a size, as every glyph in the icons package is. */
private typealias RowGlyph = @Composable (tint: Color, modifier: Modifier) -> Unit

/**
 * Who this device is signed in as, said in the two lines the top of the phone's
 * settings list has room for. Built by the navigation, which knows the account
 * feature - see [AccountPage] for why this feature does not.
 */
data class AccountSummary(
    val title: String,
    val detail: String,
    /** The first letter of the account's name, or null while signed out. */
    val initial: String?,
)

/**
 * Settings on a phone: a list of the groups, each saying what it is set to now,
 * each opening a page of its own.
 *
 * A phone's width cannot hold five segments and a sixth control beside the
 * title without cutting every label short, and a page that switched groups in
 * place was one long scroll whichever group was open. A list says where
 * everything lives in one screenful, and the values on it answer the commonest
 * question - what is it set to? - without opening anything. Wider windows keep
 * [SettingsBody], where the room is.
 */
@Composable
internal fun SettingsHub(
    settings: AppSettings,
    reader: ReaderSettings,
    translation: TranslationSettings,
    onOpen: (SettingsArea) -> Unit,
    modifier: Modifier = Modifier,
    /** Null leaves the account card off - see [SettingsBody]'s `account`. */
    account: AccountSummary? = null,
) {
    val dimens = LocalLukiaDimens.current

    Column(
        verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing),
        modifier = modifier.padding(horizontal = dimens.screenPadding),
    ) {
        Text(
            text = stringResource(Res.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .padding(start = Space.xs)
                .semantics { heading() },
        )

        if (account != null) {
            GroupCard {
                AccountRow(summary = account, onClick = { onOpen(SettingsArea.Account) })
            }
        }

        GroupsCard(settings = settings, reader = reader, translation = translation, onOpen = onOpen)

        GroupCard {
            SettingsLinkRow(
                label = SettingsArea.About.pageTitle(),
                value = LukiaVersion,
                glyph = { tint, glyph -> InfoGlyph(tint, glyph) },
                onClick = { onOpen(SettingsArea.About) },
            )
        }
    }
}

/** The four groups a reader comes to change, in one card. */
@Composable
private fun GroupsCard(
    settings: AppSettings,
    reader: ReaderSettings,
    translation: TranslationSettings,
    onOpen: (SettingsArea) -> Unit,
) {
    GroupCard {
        Column {
            SettingsLinkRow(
                label = SettingsArea.Appearance.pageTitle(),
                value = settings.palette.displayName(),
                glyph = { tint, glyph -> AppearanceGlyph(tint, glyph) },
                onClick = { onOpen(SettingsArea.Appearance) },
            )
            RowDivider()
            SettingsLinkRow(
                label = SettingsArea.Reading.pageTitle(),
                value = stringResource(
                    Res.string.settings_reading_summary,
                    reader.typeface.displayName(),
                    reader.textScalePercent,
                ),
                glyph = { tint, glyph -> OpenBookGlyph(tint, glyph) },
                onClick = { onOpen(SettingsArea.Reading) },
            )
            RowDivider()
            SettingsLinkRow(
                label = SettingsArea.Translating.pageTitle(),
                value = translation.targetLanguage.endonym,
                glyph = { tint, glyph -> TranslateGlyph(tint, glyph) },
                onClick = { onOpen(SettingsArea.Translating) },
            )
            RowDivider()
            SettingsLinkRow(
                label = SettingsArea.AiTranslation.pageTitle(),
                value = stringResource(if (translation.aiTranslation) Res.string.state_on else Res.string.state_off),
                glyph = { tint, glyph -> SparkleGlyph(tint, glyph) },
                onClick = { onOpen(SettingsArea.AiTranslation) },
                beta = true,
            )
        }
    }
}

/**
 * One group of settings as a row: its glyph, its name, what it is set to, and the
 * chevron that says it opens rather than toggles.
 *
 * The name takes the width and wraps at large type; the value keeps to one line
 * and gives way first.
 */
@Composable
private fun SettingsLinkRow(
    label: String,
    value: String,
    glyph: RowGlyph,
    onClick: () -> Unit,
    beta: Boolean = false,
) {
    LinkRow(label = label, onClick = onClick) {
        GlyphTile(glyph)

        Spacer(modifier = Modifier.width(Space.lg))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f, fill = false),
            )

            if (beta) BetaBadge(modifier = Modifier.padding(start = Space.sm))
        }

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = Space.md)
                .widthIn(max = ValueMaxWidth),
        )
    }
}

/**
 * A setting whose choices are a page of their own: its name, what it is set to,
 * and the chevron. For a list too long to sit open on a phone's page - see the
 * language a book is translated into.
 */
@Composable
internal fun SettingsValueRow(label: String, value: String, onClick: () -> Unit) {
    LinkRow(label = label, onClick = onClick) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = Space.md)
                .widthIn(max = ValueMaxWidth),
        )
    }
}

/**
 * The card at the top of the list: who this device syncs as, or the way to
 * start. A card of its own because it is about the reader rather than about the
 * app, the way a phone's own settings open on the person they belong to.
 */
@Composable
private fun AccountRow(summary: AccountSummary, onClick: () -> Unit) {
    LinkRow(label = summary.title, onClick = onClick) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(AvatarSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = LukiaAlpha.Tonal)),
        ) {
            if (summary.initial != null) {
                Text(
                    text = summary.initial,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                CloudGlyph(tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(TileGlyphSize + Space.xs))
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(Space.xxs),
            modifier = Modifier
                .weight(1f)
                .padding(start = Space.lg),
        ) {
            Text(
                text = summary.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** What every row in the list shares: the press, its height, and the chevron at the end. */
@Composable
private fun LinkRow(
    label: String,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // Square: the card clips the first and last rows to its own corners.
            .pressable(
                interactionSource = interaction,
                shape = RectangleShape,
                onClickLabel = stringResource(Res.string.settings_open_area, label),
                onClick = onClick,
            )
            .heightIn(min = RowMinHeight)
            .padding(horizontal = Space.lg, vertical = Space.md),
    ) {
        content()

        ForwardChevron(
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = LukiaAlpha.Disabled),
            modifier = Modifier
                .padding(start = Space.md)
                .size(ChevronSize),
        )
    }
}

/**
 * A group's glyph on a tinted tile, in the accent - one colour for all of them,
 * so the list wears the palette the reader chose rather than a rainbow of its own.
 */
@Composable
private fun GlyphTile(glyph: RowGlyph) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(TileSize)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = LukiaAlpha.Tonal)),
    ) {
        glyph(MaterialTheme.colorScheme.primary, Modifier.size(TileGlyphSize))
    }
}

/** Aligned to the names rather than the tiles, as iOS insets a list's separators. */
@Composable
private fun RowDivider() {
    LukiaDivider(inset = Space.lg + TileSize + Space.lg)
}
