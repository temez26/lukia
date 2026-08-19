package io.lukia.features.reader.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.lukia.features.reader.presentation.ShownTranslations
import io.lukia.features.reader.presentation.TranslateOnScreen
import io.lukia.features.translate.domain.BulkTranslation
import io.lukia.shared.presentation.components.LukiaIconButton
import io.lukia.shared.presentation.components.ShortcutTooltip
import io.lukia.shared.presentation.components.TranslateGlyph
import io.lukia.shared.presentation.components.TranslateOnwardsGlyph
import io.lukia.shared.presentation.components.TranslationsShownGlyph

/**
 * What the toolbar needs to know about translating, gathered so the bar takes it
 * as one thing.
 *
 * These seven always travel together - they are one feature's worth of state and
 * the callbacks that change it - and passing them separately is what made the
 * bar impossible to lift out of the screen.
 */
internal class ReaderTranslationUi(
    val configured: Boolean,
    val onScreen: TranslateOnScreen,
    val shown: ShownTranslations,
    val offered: BulkTranslation,
    val onTranslateOnScreen: () -> Unit,
    val onContinuousChange: (Boolean) -> Unit,
    val onVisibleChange: (Boolean) -> Unit,
)

/**
 * Translating what is on screen, once or from now on.
 *
 * Two buttons rather than one with a mode, because they answer different
 * questions: "do this page" is something a reader does at a page they are stuck
 * on, and "keep doing it" is a decision about the rest of the session. Folding
 * them together would make the first one a two-step operation.
 *
 * The standing one shows its state in its own colour rather than by changing
 * shape - it is the one control in the bar that stays on after it is pressed,
 * and a tinted glyph is how the rest of the app says that.
 *
 * Shown at all only when something is set up to translate with. A button that
 * can only produce an error is worse than no button.
 */
@Composable
internal fun TranslationControls(
    continuous: Boolean,
    shown: ShownTranslations,
    offered: BulkTranslation,
    onTranslateOnScreen: () -> Unit,
    onContinuousChange: (Boolean) -> Unit,
    onVisibleChange: (Boolean) -> Unit,
) {
    // Not there at all until the reader has asked for them in Settings. These
    // two are the ones that spend a screenful at a time - see BulkTranslation -
    // and a button that can do that sitting in the bar of a reading app is a
    // button somebody presses to find out what it does.
    if (offered.onScreen) {
        ShortcutTooltip(description = "Translate what is on screen", shortcut = "R") {
            LukiaIconButton(
                label = "Translate what is on screen",
                onClick = onTranslateOnScreen,
            ) { tint, glyph ->
                TranslateGlyph(tint = tint, modifier = glyph)
            }
        }
    }

    if (offered.asYouRead) {
        ShortcutTooltip(
            description = if (continuous) {
                "Stop translating as you read"
            } else {
                "Keep translating as you read"
            },
            shortcut = "E",
        ) {
            LukiaIconButton(
                label = if (continuous) {
                    "Stop translating as you read"
                } else {
                    "Keep translating as you read"
                },
                onClick = { onContinuousChange(!continuous) },
                tint = if (continuous) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) { tint, glyph ->
                TranslateOnwardsGlyph(tint = tint, modifier = glyph)
            }
        }
    }

    // Nothing to put away until something has been translated, so the control is
    // there from the start - where the reader will look for it - but does nothing
    // until there is something for it to do.
    ShortcutTooltip(
        description = if (shown.visible) "Hide the translations" else "Show the translations",
        shortcut = "H",
    ) {
        LukiaIconButton(
            label = if (shown.visible) "Hide the translations" else "Show the translations",
            onClick = { onVisibleChange(!shown.visible) },
            enabled = shown.any,
            tint = if (shown.visible) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ) { tint, glyph ->
            TranslationsShownGlyph(tint = tint, shown = shown.visible, modifier = glyph)
        }
    }
}

/** A transparent press catcher covering the reader. */
@Composable
internal fun DismissLayer(onDismiss: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onDismiss,
            ),
    )
}
