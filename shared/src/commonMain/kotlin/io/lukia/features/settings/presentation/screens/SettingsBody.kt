package io.lukia.features.settings.presentation.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.settings.domain.AppSettings
import io.lukia.features.settings.presentation.ProviderActions
import io.lukia.features.settings.presentation.ProviderState
import io.lukia.features.settings.presentation.TranslatingActions
import io.lukia.features.translate.domain.settings.TranslationSettings
import io.lukia.resources.Res
import io.lukia.resources.settings_title
import io.lukia.shared.presentation.components.controls.pressFeedback
import io.lukia.shared.presentation.theme.Clearance
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.LukiaMotion
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/** The Account page, as the account feature builds it - see [SettingsArea.Account]. */
typealias AccountPage = @Composable (groupColor: Color) -> Unit

/**
 * How long one group takes to give way to the next. The app's own quick
 * duration - see [LukiaMotion] - so switching groups moves at the rate a menu does.
 */
private const val AreaFadeMillis = LukiaMotion.QuickMillis

/** Wide enough for the longest group's name at the default size, beside its glyph. */
private val SidebarWidth: Dp = 220.dp

/**
 * The settings themselves, without the chrome around them, as a window shows
 * them: the groups listed down a sidebar, and the one chosen beside it.
 *
 * A sidebar rather than a row of segments: six groups did not fit one row at a
 * readable size, and a page that changed height with every group moved the row
 * under the pointer. The sheet holds one height; only the group scrolls.
 *
 * A phone shows the same groups as a list of pages - see `SettingsHub` - and
 * both build them from [SettingsAreaContent], so a setting reads the same either
 * way.
 */
@Composable
internal fun SettingsBody(
    settings: AppSettings,
    reader: ReaderSettings,
    translation: TranslationSettings,
    providerState: ProviderState,
    onSettingsChange: (AppSettings) -> Unit,
    onReaderChange: (ReaderSettings) -> Unit,
    translatingActions: TranslatingActions,
    providerActions: ProviderActions,
    modifier: Modifier = Modifier,
    topClearance: Dp = Space.sm,
    bottomClearance: Dp = Clearance.pageBottom,
    /**
     * What a group sits in. It has to contrast with whatever is behind this body,
     * and that differs: the screen lays it on the window background, the panel on
     * a raised surface, and in the dark theme the background is true black.
     */
    groupColor: Color = MaterialTheme.colorScheme.surface,
    /**
     * The diagnostics group, which sits under About - see `DiagnosticsSettings`.
     * A slot rather than more parameters because it is the one group here with a
     * view model of its own, and this body stays composable without a dependency
     * graph behind it. Composed only while the developer settings are on - see
     * [AppSettings.developerMode] - so the graph is never asked for that view
     * model on a page that would not show it.
     */
    diagnostics: (@Composable () -> Unit)? = null,
    /**
     * The Account page, which the account feature builds out of this feature's
     * controls - so it arrives as a slot, from the navigation that knows both,
     * rather than being named here and making the two features a circle. Null
     * leaves it off the page and out of the sidebar.
     */
    account: AccountPage? = null,
    /** The page it opens on - the Account page, when that is what was asked for. */
    initialArea: SettingsArea = SettingsArea.Default,
) {
    val dimens = LocalLukiaDimens.current
    var area by rememberSaveable(initialArea) { mutableStateOf(initialArea) }

    Row(modifier = modifier) {
        SettingsSidebar(
            area = area,
            onAreaChange = { area = it },
            showAccount = account != null,
            modifier = Modifier
                .width(SidebarWidth)
                .fillMaxHeight()
                .padding(top = topClearance, bottom = dimens.sectionSpacing),
        )

        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant),
        )

        // One group fades into the next in place; the sheet around them keeps
        // its height, so nothing else moves.
        Crossfade(
            targetState = area,
            animationSpec = tween(AreaFadeMillis, easing = LukiaMotion.Standard),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) { shown ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = dimens.screenPadding),
            ) {
                Spacer(modifier = Modifier.height(topClearance))

                Text(
                    text = shown.pageTitle(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )

                SettingsAreaContent(
                    area = shown,
                    settings = settings,
                    reader = reader,
                    translation = translation,
                    providerState = providerState,
                    onSettingsChange = onSettingsChange,
                    onReaderChange = onReaderChange,
                    translatingActions = translatingActions,
                    providerActions = providerActions,
                    groupColor = groupColor,
                    modifier = Modifier.padding(top = dimens.sectionSpacing),
                    diagnostics = diagnostics,
                    account = account,
                )

                Spacer(modifier = Modifier.height(bottomClearance))
            }
        }
    }
}

/**
 * The page's name over its groups, one row each, with Account kept apart at the
 * foot: it is about the device rather than about reading, and most readers open
 * it once.
 */
@Composable
private fun SettingsSidebar(
    area: SettingsArea,
    onAreaChange: (SettingsArea) -> Unit,
    showAccount: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Space.xs),
        modifier = modifier
            .padding(horizontal = Space.md)
            .selectableGroup(),
    ) {
        Text(
            text = stringResource(Res.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(horizontal = Space.sm)
                .padding(bottom = Space.lg)
                .semantics { heading() },
        )

        SettingsArea.Segments.forEach { group ->
            SidebarRow(group = group, selected = group == area, onClick = { onAreaChange(group) })
        }

        if (showAccount) {
            Spacer(modifier = Modifier.weight(1f))

            SidebarRow(
                group = SettingsArea.Account,
                selected = area == SettingsArea.Account,
                onClick = { onAreaChange(SettingsArea.Account) },
            )
        }
    }
}

/** One group in the sidebar: its glyph on a tile and its name, lit while it is the one shown. */
@Composable
private fun SidebarRow(group: SettingsArea, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.small

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .pressFeedback(interactionSource, shape)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(Space.sm),
    ) {
        GlyphTile { tint, glyph -> group.Glyph(tint, glyph) }

        Spacer(modifier = Modifier.width(Space.md))

        Text(
            text = group.pageTitle(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )

        if (group == SettingsArea.AiTranslation) BetaBadge(modifier = Modifier.padding(start = Space.sm))
    }
}
