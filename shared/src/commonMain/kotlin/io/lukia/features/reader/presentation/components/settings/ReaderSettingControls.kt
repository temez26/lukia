package io.lukia.features.reader.presentation.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.resources.Res
import io.lukia.resources.reader_decrease_text_size
import io.lukia.resources.reader_increase_text_size
import io.lukia.resources.reader_percent
import io.lukia.resources.reader_text_size_percent
import io.lukia.shared.presentation.components.controls.pressable
import io.lukia.shared.presentation.components.surface.SectionLabel
import io.lukia.shared.presentation.theme.LocalLukiaMetrics
import io.lukia.shared.presentation.theme.LukiaAlpha
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/**
 * What a control gets in the wide layout. Fixed rather than a weight, so every
 * control in the panel starts at the same place down the column - five width
 * presets and two typeface options line up instead of each dividing whatever its
 * own label left over.
 */
private val WideControlWidth: Dp = 224.dp

// The parts the reader's settings panel is built from: a labelled row, a
// heading, and the stepper that sets text size. Separate from the panel itself
// because none of them knows what a reader setting is - each takes a value and
// reports a change.

/**
 * A label and its control.
 *
 * On a phone the label sits above, because three segments beside a label is what
 * makes segment text wrap on a narrow display. Given a wider window the two sit
 * on one row, which is both what a desktop panel looks like and what stops this
 * one from being a tall phone column with air either side of it.
 */
@Composable
internal fun SettingGroup(
    label: String,
    wide: Boolean,
    inset: Dp,
    enabled: Boolean = true,
    control: @Composable () -> Unit,
) {
    // Wide and stacked put the control in two different places in the tree, and
    // a window dragged across the boundary moves it from one to the other. Made
    // movable so it takes its state with it - otherwise resizing the panel past
    // the wide threshold resets whatever the control remembers, which for the
    // text-size stepper is the press it is in the middle of.
    val slot = remember(control) { movableContentOf(control) }

    if (wide) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = inset, vertical = Space.xs),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = if (enabled) 1f else LukiaAlpha.Disabled,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            // A floor rather than a fixed width. Five segments reading "Tight",
            // "Normal" and "Relaxed" fit 224dp in English and not in every
            // language, and a control given exactly that much has nowhere to
            // grow into - the label beside it does, because it is weighted.
            Box(modifier = Modifier.widthIn(min = WideControlWidth)) {
                slot()
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = inset, vertical = Space.sm),
        ) {
            SectionLabel(
                text = label,
                enabled = enabled,
                modifier = Modifier.padding(bottom = Space.sm),
            )

            slot()
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
internal fun SectionHeading(
    label: String,
    wide: Boolean,
    inset: Dp,
    enabled: Boolean = true,
) {
    if (!wide) return

    SectionLabel(
        text = label,
        enabled = enabled,
        modifier = Modifier.padding(
            start = inset,
            end = inset,
            top = Space.sm,
            bottom = Space.xxs,
        ),
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
internal fun TextSizeStepper(
    settings: ReaderSettings,
    enabled: Boolean,
    onSettingsChange: (ReaderSettings) -> Unit,
) {
    val metrics = LocalLukiaMetrics.current

    // Read before the modifier chain: `semantics` is not a composable scope.
    val spoken = stringResource(Res.string.reader_text_size_percent, settings.textScalePercent)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 1f else 0.5f),
            )
            .height(metrics.controlHeight)
            .padding(Space.xxs),
    ) {
        StepperButton(
            symbolSize = 12.sp,
            label = stringResource(Res.string.reader_decrease_text_size),
            enabled = enabled && settings.canReduceText,
            onClick = { onSettingsChange(settings.withSmallerText()) },
        )

        Text(
            text = stringResource(Res.string.reader_percent, settings.textScalePercent),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface.copy(
                alpha = if (enabled) 1f else LukiaAlpha.Disabled,
            ),
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = spoken },
        )

        StepperButton(
            symbolSize = 17.sp,
            label = stringResource(Res.string.reader_increase_text_size),
            enabled = enabled && settings.canEnlargeText,
            onClick = { onSettingsChange(settings.withLargerText()) },
        )
    }
}

/**
 * The two ends of the size range, drawn as a small "A" and a large one. The
 * letter is decoration, so [label] is what a screen reader announces.
 *
 * Not a [io.lukia.shared.presentation.components.controls.LukiaIconButton]: it carries a
 * letter rather than a glyph, and it fills its end of the stepper track rather
 * than being a square target of its own. It wears the same hover and focus
 * treatment as one.
 */
@Composable
private fun StepperButton(
    symbolSize: TextUnit,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
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
            .pressable(
                interactionSource = interactionSource,
                shape = shape,
                onClickLabel = label,
                onClick = onClick,
                enabled = enabled,
            )
            .semantics { contentDescription = label },
    ) {
        Text(
            text = "A",
            fontSize = symbolSize,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(
                alpha = if (enabled) 1f else LukiaAlpha.Disabled,
            ),
        )
    }
}

/**
 * A line of small print under a control, saying what the setting above it is
 * subject to.
 *
 * Placed after the control it explains in the tree but read as belonging to it,
 * which is what a screen reader does with it too. Small and grey: it is a note,
 * and a panel where the explanations are as loud as the settings is a panel
 * nobody reads either.
 */
@Composable
internal fun PanelNote(text: String, inset: Dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = inset,
            end = inset,
            top = Space.xxs,
            bottom = Space.md,
        ),
    )
}
