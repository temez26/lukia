package io.lukia.features.reader.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lukia.features.reader.domain.LineSpacing
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.domain.ReaderTheme
import io.lukia.features.reader.domain.ReaderTypeface
import io.lukia.features.reader.domain.ReadingWidth
import io.lukia.shared.presentation.components.DismissOnBack
import io.lukia.shared.presentation.components.GlassSurface
import io.lukia.shared.presentation.components.GlassTone
import io.lukia.shared.presentation.components.LukiaDivider
import io.lukia.shared.presentation.components.SectionLabel
import io.lukia.shared.presentation.components.SegmentedControl
import io.lukia.shared.presentation.components.focusRing
import io.lukia.shared.presentation.components.hoverHighlight
import io.lukia.shared.presentation.components.popoverTransition
import io.lukia.shared.presentation.components.rememberPopoverProgress
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.LocalLukiaMetrics
import io.lukia.shared.presentation.theme.LukiaAlpha
import io.lukia.shared.presentation.theme.Space
import io.lukia.shared.presentation.theme.isWideWindow
import kotlin.math.roundToInt

/** Grows out of the control it hangs from, matching the book actions menu. */
private val PanelTransformOrigin = TransformOrigin(1f, 0f)

/** Wide enough for three segments of readable type, narrow enough for a phone. */
private val CompactPanelWidth: Dp = 284.dp

/**
 * On a window with room, the panel is wider and its labels sit beside their
 * controls rather than above them. A phone stacks them because it has no choice;
 * carrying that stack onto a desktop is what makes a panel read as a phone
 * layout that happened to be opened on a monitor.
 */
private val WidePanelWidth: Dp = 380.dp

/**
 * What a control gets in the wide layout. Fixed rather than a weight, so every
 * control in the panel starts at the same place down the column - five width
 * presets and two typeface options line up instead of each dividing whatever its
 * own label left over.
 */
private val WideControlWidth: Dp = 224.dp

/** How far the panel may grow before it scrolls inside itself. */
private val CompactPanelMaxHeight: Dp = 440.dp
private val WidePanelMaxHeight: Dp = 520.dp

/**
 * The reader's presentation settings.
 *
 * A panel hanging from the toolbar rather than a sheet: adjusting the theme or
 * the measure while the page is hidden behind a full-width sheet would mean
 * dismissing it after every change to see what it did. Nothing behind it is
 * dimmed for the same reason.
 *
 * [typographyEnabled] is false for a document that carries its own fixed layout.
 * A PDF's type was set when the file was made, so text size, spacing and face
 * have nothing to act on - they are shown, and disabled, rather than hidden, so
 * they are not mistaken for missing.
 */
@Composable
internal fun ReaderSettingsPanel(
    expanded: Boolean,
    settings: ReaderSettings,
    typographyEnabled: Boolean,
    onSettingsChange: (ReaderSettings) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = rememberPopoverProgress(expanded, label = "readerSettingsPanel")

    if (!expanded && progress == 0f) return

    DismissOnBack(enabled = expanded, onDismiss = onDismissRequest)

    val wide = isWideWindow
    val inset = LocalLukiaDimens.current.panelPadding

    GlassSurface(
        shape = MaterialTheme.shapes.large,
        tone = GlassTone.Panel,
        modifier = modifier.popoverTransition(progress, PanelTransformOrigin)
    ) {
        Column(
            modifier = Modifier
                .width(if (wide) WidePanelWidth else CompactPanelWidth)
                // A short window - a phone in landscape - would otherwise
                // leave the last setting past the bottom edge with no way to
                // reach it.
                .heightIn(max = if (wide) WidePanelMaxHeight else CompactPanelMaxHeight)
                .verticalScroll(rememberScrollState())
                .padding(vertical = Space.md)
        ) {
            SectionHeading(label = "Page", wide = wide, inset = inset)

            SettingGroup(label = "Theme", wide = wide, inset = inset) {
                SegmentedControl(
                    options = ReaderTheme.entries,
                    selected = settings.theme,
                    label = { it.name },
                    onSelect = { onSettingsChange(settings.copy(theme = it)) }
                )
            }

            // Shown as percentages rather than names: with five steps that
            // run past the width of the window, "125%" says what "Larger"
            // cannot.
            SettingGroup(label = "Page width", wide = wide, inset = inset) {
                SegmentedControl(
                    options = ReadingWidth.entries,
                    // Nothing is selected when a pinch has left the page
                    // between two presets, which is the honest answer.
                    selected = settings.matchingWidth,
                    label = { "${(it.fraction * 100).roundToInt()}%" },
                    onSelect = { onSettingsChange(settings.withPageScale(it.fraction)) }
                )
            }

            LukiaDivider(
                inset = inset,
                endInset = inset,
                modifier = Modifier.padding(vertical = Space.md)
            )

            SectionHeading(
                label = "Text",
                wide = wide,
                inset = inset,
                enabled = typographyEnabled
            )

            // Placed before the controls it explains, so it is read first
            // whether by eye or by screen reader.
            if (!typographyEnabled) {
                Text(
                    text = "Text settings apply to reflowable books - this one " +
                        "carries its own fixed layout.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = inset,
                        end = inset,
                        top = Space.xxs,
                        bottom = Space.md
                    )
                )
            }

            SettingGroup(
                label = "Text size",
                wide = wide,
                inset = inset,
                enabled = typographyEnabled
            ) {
                TextSizeStepper(
                    settings = settings,
                    enabled = typographyEnabled,
                    onSettingsChange = onSettingsChange
                )
            }

            SettingGroup(
                label = "Line spacing",
                wide = wide,
                inset = inset,
                enabled = typographyEnabled
            ) {
                SegmentedControl(
                    options = LineSpacing.entries,
                    selected = settings.lineSpacing,
                    label = { it.name },
                    enabled = typographyEnabled,
                    onSelect = { onSettingsChange(settings.copy(lineSpacing = it)) }
                )
            }

            SettingGroup(
                label = "Typeface",
                wide = wide,
                inset = inset,
                enabled = typographyEnabled
            ) {
                SegmentedControl(
                    options = ReaderTypeface.entries,
                    selected = settings.typeface,
                    label = { if (it == ReaderTypeface.Serif) "Serif" else "Sans" },
                    enabled = typographyEnabled,
                    onSelect = { onSettingsChange(settings.copy(typeface = it)) }
                )
            }

            if (wide) {
                LukiaDivider(
                    inset = inset,
                    endInset = inset,
                    modifier = Modifier.padding(vertical = Space.md)
                )
                SectionHeading(label = "Keyboard", wide = true, inset = inset)
                ShortcutList(reflowable = typographyEnabled, inset = inset)
            }
        }
    }
}

/**
 * What the keys do.
 *
 * Shortcuts nobody can find are shortcuts nobody has. The bar's own controls
 * carry theirs in a tooltip, but turning pages has no button to hang one off -
 * and this is the panel a reader opens when they want to know how the reader
 * works. Only in the wide layout, since only the windows with room for it have
 * keyboards.
 */
@Composable
private fun ShortcutList(reflowable: Boolean, inset: Dp) {
    val shortcuts = listOf(
        "Turn page" to "Space  PgDn  PgUp",
        "Scroll" to "↑  ↓",
        "Start, end" to "Home  End",
        // Named for what the keys do rather than for the setting they move, so
        // the panel does not carry the words "Page width" twice - and what they
        // do depends on the book, since there is no widening a re-set page.
        (if (reflowable) "Larger, smaller" else "Wider, narrower") to "+  −  0",
        "Contents, settings" to "C  T",
        "Close book" to "Esc"
    )

    shortcuts.forEach { (action, keys) ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = inset, vertical = Space.xxs)
        ) {
            Text(
                text = action,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = keys,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

/**
 * A label and its control.
 *
 * On a phone the label sits above, because three segments beside a label is what
 * makes segment text wrap on a narrow display. Given a wider window the two sit
 * on one row, which is both what a desktop panel looks like and what stops this
 * one from being a tall phone column with air either side of it.
 */
@Composable
private fun SettingGroup(
    label: String,
    wide: Boolean,
    inset: Dp,
    enabled: Boolean = true,
    control: @Composable () -> Unit
) {
    if (wide) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = inset, vertical = Space.xs)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = if (enabled) 1f else LukiaAlpha.Disabled
                ),
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )

            Box(modifier = Modifier.width(WideControlWidth)) {
                control()
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = inset, vertical = Space.sm)
        ) {
            SectionLabel(
                text = label,
                enabled = enabled,
                modifier = Modifier.padding(bottom = Space.sm)
            )

            control()
        }
    }
}

/**
 * What the settings under it act on.
 *
 * Only in the wide layout: there the labels are full-sized and on the same row
 * as their controls, so without a heading above them the panel is one
 * undifferentiated list. The stacked layout already reads as groups, its small
 * grey labels doing the separating, and a second tier of headings there would be
 * more chrome than settings.
 */
@Composable
private fun SectionHeading(
    label: String,
    wide: Boolean,
    inset: Dp,
    enabled: Boolean = true
) {
    if (!wide) return

    SectionLabel(
        text = label,
        enabled = enabled,
        modifier = Modifier.padding(
            start = inset,
            end = inset,
            top = Space.sm,
            bottom = Space.xxs
        )
    )
}

/**
 * Smaller / current size / larger, on the same track as the segmented controls
 * so the panel reads as one set of controls.
 *
 * The buttons disable themselves at either end of the range rather than
 * silently doing nothing, which is also what tells assistive technology the
 * limit has been reached.
 */
@Composable
private fun TextSizeStepper(
    settings: ReaderSettings,
    enabled: Boolean,
    onSettingsChange: (ReaderSettings) -> Unit
) {
    val metrics = LocalLukiaMetrics.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 1f else 0.5f)
            )
            .height(metrics.controlHeight)
            .padding(2.dp)
    ) {
        StepperButton(
            symbolSize = 12.sp,
            label = "Decrease text size",
            enabled = enabled && settings.canReduceText,
            onClick = { onSettingsChange(settings.withSmallerText()) }
        )

        Text(
            text = "${settings.textScalePercent}%",
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface.copy(
                alpha = if (enabled) 1f else LukiaAlpha.Disabled
            ),
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "Text size ${settings.textScalePercent} percent" }
        )

        StepperButton(
            symbolSize = 17.sp,
            label = "Increase text size",
            enabled = enabled && settings.canEnlargeText,
            onClick = { onSettingsChange(settings.withLargerText()) }
        )
    }
}

/**
 * The two ends of the size range, drawn as a small "A" and a large one. The
 * letter is decoration, so [label] is what a screen reader announces.
 *
 * Not a [io.lukia.shared.presentation.components.LukiaIconButton]: it carries a
 * letter rather than a glyph, and it fills its end of the stepper track rather
 * than being a square target of its own. It wears the same hover and focus
 * treatment as one.
 */
@Composable
private fun StepperButton(
    symbolSize: TextUnit,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.extraSmall

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxHeight()
            .width(52.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .hoverHighlight(interactionSource, shape)
            .focusRing(interactionSource, shape)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = label,
                onClick = onClick
            )
            .semantics { contentDescription = label }
    ) {
        Text(
            text = "A",
            fontSize = symbolSize,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(
                alpha = if (enabled) 1f else LukiaAlpha.Disabled
            )
        )
    }
}

/**
 * The "Aa" that opens the panel, sized to read as type rather than as an icon.
 *
 * Two letters are wider than the square its button hands every other glyph, so it
 * is measured unbounded and centred in that square rather than laid out inside
 * it - constrained to the square, the second letter is simply clipped away.
 */
@Composable
internal fun TextSettingsGlyph(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Text(
            text = "Aa",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Serif,
            color = tint,
            maxLines = 1,
            modifier = Modifier.wrapContentSize(unbounded = true)
        )
    }
}
