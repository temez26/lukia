package io.lukia.features.settings.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.settings.domain.AppSettings
import io.lukia.features.settings.domain.AppTheme
import io.lukia.features.settings.presentation.SettingsViewModel
import io.lukia.shared.presentation.components.BackChevron
import io.lukia.shared.presentation.components.GroupCard
import io.lukia.shared.presentation.components.IconButtonVariant
import io.lukia.shared.presentation.components.LukiaIconButton
import io.lukia.shared.presentation.components.SectionLabel
import io.lukia.shared.presentation.components.SegmentedControl
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import org.koin.compose.viewmodel.koinViewModel

/** Vertical room reserved so content starts clear of the floating back control. */
private val TopChromeClearance: Dp = 68.dp

/** Room past the last group, so the settings do not end hard against the edge. */
private val SettingsBottomClearance: Dp = 32.dp

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val settings by viewModel.settings.collectAsState()

    SettingsContent(
        settings = settings,
        onThemeChange = viewModel::setTheme,
        onBack = onBack
    )
}

/**
 * Stateless half of the screen, matching the other screens so the layout can be
 * rendered without a ViewModel or dependency graph.
 *
 * This screen keeps a title where the library dropped one. A pushed screen has to
 * say what it is - the library is the app's home and needs no introduction, but
 * arriving here from a menu does.
 */
@Composable
fun SettingsContent(
    settings: AppSettings,
    onThemeChange: (AppTheme) -> Unit,
    onBack: () -> Unit
) {
    val dimens = LocalLukiaDimens.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Full-bleed like the detail screen, so content passes under the floating
        // back button - which means window insets are applied by hand. The
        // background above stays outside the inset so colour still reaches the
        // screen edges. Resolves to zero on desktop.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            SettingsBody(
                settings = settings,
                onThemeChange = onThemeChange,
                modifier = Modifier.fillMaxSize()
            )

            LukiaIconButton(
                label = "Go back",
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = dimens.screenPadding, top = Space.lg),
                variant = IconButtonVariant.Glass
            ) { tint, glyph ->
                BackChevron(tint = tint, modifier = glyph)
            }
        }
    }
}

/**
 * The settings themselves, without the chrome around them.
 *
 * Shared, because these are shown two ways - as a screen of its own on a phone,
 * and as a panel over the library on anything wider - and a setting must not
 * appear or read differently depending on which one you reached.
 *
 * It keeps its title where the library dropped one. The library is the app's home
 * and needs no introduction; arriving here from a menu does.
 */
@Composable
internal fun SettingsBody(
    settings: AppSettings,
    onThemeChange: (AppTheme) -> Unit,
    modifier: Modifier = Modifier,
    topClearance: Dp = TopChromeClearance,
    bottomClearance: Dp = SettingsBottomClearance,
    /**
     * What a group sits in. It has to contrast with whatever is behind this body,
     * and that differs: the screen lays it on the window background, the panel on
     * a raised surface, and in the dark theme the background is true black.
     */
    groupColor: Color = MaterialTheme.colorScheme.surface
) {
    val dimens = LocalLukiaDimens.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(topClearance))

        Column(
            modifier = Modifier
                .widthIn(max = dimens.maxDetailWidth)
                .fillMaxWidth()
                .padding(horizontal = dimens.screenPadding)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            SettingsSection(
                title = "Appearance",
                // Named for what it does rather than what it is called, since
                // "System" is the only option whose meaning is not obvious from
                // the word alone.
                footnote = "System follows your device’s light and dark setting.",
                groupColor = groupColor,
                modifier = Modifier.padding(top = dimens.sectionSpacing)
            ) {
                SegmentedControl(
                    options = AppTheme.entries,
                    selected = settings.theme,
                    label = { it.name },
                    onSelect = onThemeChange
                )
            }
        }

        Spacer(modifier = Modifier.height(bottomClearance))
    }
}

/**
 * One group of settings, in the style of an iOS grouped list: a header above a
 * rounded surface, with any explanation set below it rather than inside.
 */
@Composable
private fun SettingsSection(
    title: String,
    groupColor: Color,
    modifier: Modifier = Modifier,
    footnote: String? = null,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel(
            text = title,
            modifier = Modifier.padding(start = Space.xs, bottom = Space.sm)
        )

        GroupCard(color = groupColor) {
            Box(modifier = Modifier.padding(Space.lg)) {
                content()
            }
        }

        if (footnote != null) {
            Text(
                text = footnote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.xs, top = Space.sm)
            )
        }
    }
}
