package io.lukia.features.reader.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import io.lukia.shared.presentation.components.SegmentedControl
import io.lukia.shared.presentation.components.popoverTransition
import io.lukia.shared.presentation.components.rememberPopoverProgress
import io.lukia.shared.presentation.theme.MinTouchTarget
import kotlin.math.roundToInt

/** Grows out of the control it hangs from, matching the book actions menu. */
private val PanelTransformOrigin = TransformOrigin(1f, 0f)

/** Wide enough for three segments of readable type, narrow enough for a phone. */
private val PanelWidth: Dp = 284.dp
private val PanelPadding: Dp = 16.dp

/** The text stepper shares the segmented control's track height. */
private val SegmentHeight: Dp = MinTouchTarget

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

    GlassSurface(
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.popoverTransition(progress, PanelTransformOrigin)
    ) {
        Column(
            modifier = Modifier
                .width(PanelWidth)
                // A short window - a phone in landscape - would otherwise
                // leave the last setting past the bottom edge with no way to
                // reach it.
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 10.dp)
        ) {
            SettingGroup(label = "Theme") {
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
            SettingGroup(label = "Page width") {
                SegmentedControl(
                    options = ReadingWidth.entries,
                    // Nothing is selected when a pinch has left the page
                    // between two presets, which is the honest answer.
                    selected = settings.matchingWidth,
                    label = { "${(it.fraction * 100).roundToInt()}%" },
                    onSelect = { onSettingsChange(settings.withPageScale(it.fraction)) }
                )
            }

            PanelDivider()

            // Placed before the controls it explains, so it is read first
            // whether by eye or by screen reader.
            if (!typographyEnabled) {
                Text(
                    text = "Text settings apply to reflowable books - this one " +
                        "carries its own fixed layout.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = PanelPadding,
                        end = PanelPadding,
                        top = 4.dp,
                        bottom = 8.dp
                    )
                )
            }

            SettingGroup(label = "Text size", enabled = typographyEnabled) {
                TextSizeStepper(
                    settings = settings,
                    enabled = typographyEnabled,
                    onSettingsChange = onSettingsChange
                )
            }

            SettingGroup(label = "Line spacing", enabled = typographyEnabled) {
                SegmentedControl(
                    options = LineSpacing.entries,
                    selected = settings.lineSpacing,
                    label = { it.name },
                    enabled = typographyEnabled,
                    onSelect = { onSettingsChange(settings.copy(lineSpacing = it)) }
                )
            }

            SettingGroup(label = "Typeface", enabled = typographyEnabled) {
                SegmentedControl(
                    options = ReaderTypeface.entries,
                    selected = settings.typeface,
                    label = { if (it == ReaderTypeface.Serif) "Serif" else "Sans" },
                    enabled = typographyEnabled,
                    onSelect = { onSettingsChange(settings.copy(typeface = it)) }
                )
            }
        }
    }
}

/**
 * A label above its control. Stacked rather than side by side so the options
 * always have the panel's full width to divide between them - three of them
 * beside a label is what makes segment text wrap on narrow displays.
 */
@Composable
private fun SettingGroup(
    label: String,
    enabled: Boolean = true,
    control: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PanelPadding, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                alpha = if (enabled) 1f else DisabledAlpha
            ),
            modifier = Modifier.padding(bottom = 5.dp)
        )

        control()
    }
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 1f else 0.5f)
            )
            .height(SegmentHeight)
            .padding(3.dp)
    ) {
        StepperButton(
            symbolSize = 13.sp,
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
                alpha = if (enabled) 1f else DisabledAlpha
            ),
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "Text size ${settings.textScalePercent} percent" }
        )

        StepperButton(
            symbolSize = 19.sp,
            label = "Increase text size",
            enabled = enabled && settings.canEnlargeText,
            onClick = { onSettingsChange(settings.withLargerText()) }
        )
    }
}

/**
 * The two ends of the size range, drawn as a small "A" and a large one. The
 * letter is decoration, so [label] is what a screen reader announces.
 */
@Composable
private fun StepperButton(
    symbolSize: TextUnit,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxHeight()
            .width(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label }
    ) {
        Text(
            text = "A",
            fontSize = symbolSize,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(
                alpha = if (enabled) 1f else DisabledAlpha
            )
        )
    }
}

/** The "Aa" that opens the panel, sized to read as type rather than as an icon. */
@Composable
internal fun TextSettingsGlyph(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Text(
        text = "Aa",
        style = MaterialTheme.typography.titleMedium,
        fontFamily = FontFamily.Serif,
        color = tint,
        modifier = modifier
    )
}

@Composable
private fun PanelDivider() {
    HorizontalDivider(
        thickness = Dp.Hairline,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

private const val DisabledAlpha = 0.38f
