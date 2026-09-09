package io.lukia.features.reader.presentation.screens.translation

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
import io.lukia.features.reader.presentation.translation.ShownTranslations
import io.lukia.features.reader.presentation.translation.TranslateOnScreen
import io.lukia.features.translate.domain.BulkTranslation
import io.lukia.features.translate.domain.settings.TargetLanguage
import io.lukia.features.translate.domain.settings.TranslationGestures
import io.lukia.resources.Res
import io.lukia.resources.reader_hide_translations
import io.lukia.resources.reader_keep_translating
import io.lukia.resources.reader_show_translations
import io.lukia.resources.reader_stop_translating
import io.lukia.resources.reader_translate_on_screen
import io.lukia.shared.presentation.components.controls.LukiaIconButton
import io.lukia.shared.presentation.components.controls.ShortcutTooltip
import io.lukia.shared.presentation.components.icons.TranslateGlyph
import io.lukia.shared.presentation.components.icons.TranslateOnwardsGlyph
import io.lukia.shared.presentation.components.icons.TranslationsShownGlyph
import io.lukia.shared.presentation.theme.isWideWindow
import org.jetbrains.compose.resources.stringResource

/**
 * What the toolbar needs to know about translating, gathered so the bar takes it
 * as one thing.
 *
 * These six always travel together - they are one feature's worth of state and
 * the callbacks that change it - and passing them separately is what made the
 * bar impossible to lift out of the screen.
 */
internal class ReaderTranslationUi(
    val onScreen: TranslateOnScreen,
    val shown: ShownTranslations,
    val offered: BulkTranslation,
    val onTranslateOnScreen: () -> Unit,
    val onContinuousChange: (Boolean) -> Unit,
    val onVisibleChange: (Boolean) -> Unit,
)

/**
 * What translating is *set* to do, as opposed to what it is doing - the language
 * the book is read into and what a thumb on the page asks for.
 *
 * Apart from [ReaderTranslationUi] because the two answer different questions
 * and are shown in different places. Those are the controls of a session: press
 * this now, keep doing it, put them away. These are standing preferences that
 * live on the settings screen as well, and are repeated in the reader's panel
 * because they are the two somebody wants to change with a book open - a
 * sentence that came back in the wrong language, or a tap that keeps translating
 * a paragraph they were only skimming.
 */
internal class ReaderTranslationPreferences(
    val targetLanguage: TargetLanguage,
    val gestures: TranslationGestures,
    val notes: Boolean,
    val onTargetLanguageChange: (TargetLanguage) -> Unit,
    val onGesturesChange: (TranslationGestures) -> Unit,
    val onNotesChange: (Boolean) -> Unit,
)

/**
 * Whether the controls belong in the toolbar, which is a question about how much
 * bar there is and nothing else.
 *
 * A window being pointed at has room for three more glyphs beside the title, and
 * a mouse to hover each for a tooltip. A phone has neither: the same three
 * crowded its back button, its place in the book and its settings together and
 * left the reader guessing at unlabelled marks, so there they move into the
 * panel - see [ReaderTranslationSettings].
 *
 * Exclusive, so the controls are in exactly one place whatever the window does.
 *
 * No longer conditioned on something being set up to translate with: both of a
 * word's answers are web APIs that need nothing. What still needs a model asks
 * for itself - the two bulk controls are behind `offered`, empty without a
 * service.
 */
internal val ReaderTranslationUi.inToolbar: Boolean
    @Composable get() = isWideWindow

internal val ReaderTranslationUi.inPanel: Boolean
    @Composable get() = !isWideWindow

/**
 * Translating what is on screen, once or from now on, as glyphs in the toolbar.
 *
 * The form a window with a mouse gets. A window without one gets the same three
 * written out as rows, at the top of the reader's panel - see
 * [ReaderTranslationSettings].
 *
 * Two buttons rather than one with a mode, because they answer different
 * questions: "do this page" is what a reader does at a page they are stuck on,
 * "keep doing it" is a decision about the rest of the session. Folding them
 * together makes the first a two-step operation.
 *
 * The standing one shows its state in its own colour rather than by changing
 * shape - it is the one control in the bar that stays on after a press, and a
 * tinted glyph is how the rest of the app says that.
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
    // Each read once and used twice: the tooltip and the button it wraps say
    // the same thing, and a resource read twice is a resource that can drift.
    val onwards = stringResource(
        if (continuous) Res.string.reader_stop_translating else Res.string.reader_keep_translating,
    )
    val shownLabel = stringResource(
        if (shown.visible) Res.string.reader_hide_translations else Res.string.reader_show_translations,
    )

    // Not there at all until the reader has asked for them in Settings. These
    // two are the ones that spend a screenful at a time - see BulkTranslation -
    // and a button that can do that sitting in the bar of a reading app is a
    // button somebody presses to find out what it does.
    if (offered.onScreen) {
        ShortcutTooltip(
            description = stringResource(Res.string.reader_translate_on_screen),
            shortcut = "R",
        ) {
            LukiaIconButton(
                label = stringResource(Res.string.reader_translate_on_screen),
                onClick = onTranslateOnScreen,
            ) { tint, glyph ->
                TranslateGlyph(tint = tint, modifier = glyph)
            }
        }
    }

    if (offered.asYouRead) {
        ShortcutTooltip(
            description = onwards,
            shortcut = "E",
        ) {
            LukiaIconButton(
                label = onwards,
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
        description = shownLabel,
        shortcut = "H",
    ) {
        LukiaIconButton(
            label = shownLabel,
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
