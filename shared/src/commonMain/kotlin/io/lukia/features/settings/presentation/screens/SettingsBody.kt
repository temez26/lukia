package io.lukia.features.settings.presentation.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import io.lukia.shared.presentation.components.controls.LukiaChip
import io.lukia.shared.presentation.components.controls.SegmentedControl
import io.lukia.shared.presentation.theme.Clearance
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.LukiaMotion
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/** The Account page, as the account feature builds it - see [SettingsArea.Account]. */
typealias AccountPage = @Composable (groupColor: Color) -> Unit

/**
 * The least room a group is given, however little it has to put in it.
 *
 * Appearance is one control and AI translation is a dozen, and a page that shrank
 * to fit whichever was open made switching between them feel like opening and
 * closing something rather than moving along a row. Held at about the height of
 * a phone's content area, the shortest group still fills the view and the
 * longest is the only one that scrolls.
 */
private val MinAreaHeight: Dp = 400.dp

/**
 * How long one group takes to give way to the next, and how long the page takes
 * to reach the new height.
 *
 * The words go first and the height follows, which is the order that reads as
 * one movement rather than as two things happening to the same screen. Both are
 * the app's own durations rather than numbers of their own - see [LukiaMotion] -
 * so switching groups here moves at the rate a menu or a panel does.
 */
private const val AreaFadeMillis = LukiaMotion.QuickMillis
private const val AreaSettleMillis = LukiaMotion.StandardMillis

/**
 * The settings themselves, without the chrome around them.
 *
 * Shared, because these are shown two ways - a screen of its own on a phone, a
 * panel over the library on anything wider - and a setting must not read
 * differently depending on which one you reached.
 *
 * It keeps its title where the library dropped one: the library is the app's
 * home and needs no introduction, arriving here from a menu does.
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
    topClearance: Dp = Clearance.topChrome,
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
     * leaves it off the page and its control out of the header.
     */
    account: AccountPage? = null,
) {
    val dimens = LocalLukiaDimens.current
    val scroll = rememberScrollState()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.verticalScroll(scroll),
    ) {
        Spacer(modifier = Modifier.height(topClearance))

        Column(
            modifier = Modifier
                .widthIn(max = dimens.maxDetailWidth)
                .fillMaxWidth()
                .padding(horizontal = dimens.screenPadding),
        ) {
            // Open on the first one rather than on a menu of them. Settings is
            // five groups, not a place to navigate around: the same control the
            // library narrows itself with switches between them, in place, with
            // nothing to go back out of.
            var area by rememberSaveable { mutableStateOf(SettingsArea.Default) }

            SettingsHeader(area = area, onAreaChange = { area = it }, showAccount = account != null)

            // Back to the top of whatever was opened. Switching from a long
            // group to a short one while scrolled down otherwise lands the
            // reader at the bottom of something they have not seen the top of.
            LaunchedEffect(area) { scroll.animateScrollTo(0) }

            // One group fades into the next while the page eases between their
            // heights. Switched without either, the page jumps from the height
            // of one group to the height of another - which reads as the screen
            // being rebuilt rather than as a control being used, and is worse
            // the further apart the two groups are in length.
            Crossfade(
                targetState = area,
                animationSpec = tween(AreaFadeMillis, easing = LukiaMotion.Standard),
                modifier = Modifier
                    .animateContentSize(
                        animationSpec = tween(AreaSettleMillis, easing = LukiaMotion.Standard),
                    )
                    // Applied inside the animation, so what eases between the
                    // groups is already the held height rather than the height
                    // of whichever group happens to be shortest.
                    .heightIn(min = MinAreaHeight),
            ) { shown ->
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
            }
        }

        Spacer(modifier = Modifier.height(bottomClearance))
    }
}

/**
 * The page's name, the control that switches between its five groups, and the
 * way to the Account page beside the name.
 *
 * It keeps its title where the library dropped one: the library is the app's
 * home and needs no introduction, while arriving here from a menu does.
 */
@Composable
private fun SettingsHeader(
    area: SettingsArea,
    onAreaChange: (SettingsArea) -> Unit,
    showAccount: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )

            if (showAccount) {
                LukiaChip(
                    label = SettingsArea.Account.displayTitle(),
                    selected = area == SettingsArea.Account,
                    onClick = { onAreaChange(SettingsArea.Account) },
                )
            }
        }

        // No segment is lit while Account is open, which is what the control's
        // nullable selection is for: the page shown is none of these five.
        SegmentedControl(
            options = SettingsArea.Segments,
            selected = area.takeIf { it in SettingsArea.Segments },
            label = { it.displayTitle() },
            onSelect = onAreaChange,
            modifier = Modifier.padding(top = Space.lg),
        )
    }
}
