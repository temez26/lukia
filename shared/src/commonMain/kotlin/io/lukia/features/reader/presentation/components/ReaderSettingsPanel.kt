package io.lukia.features.reader.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.lukia.features.reader.domain.LineSpacing
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.domain.ReaderTheme
import io.lukia.features.reader.domain.ReaderTypeface
import io.lukia.features.reader.domain.ReadingWidth
import io.lukia.shared.presentation.components.GlassSurface

/** Grows out of the control it hangs from, matching the book actions menu. */
private val PanelTransformOrigin = TransformOrigin(1f, 0f)

/** Wide enough for three segments of readable type, narrow enough for a phone. */
private val PanelWidth: Dp = 284.dp
private val PanelPadding: Dp = 16.dp

/** The segmented track. Its segments are the touch target, full width each. */
private val SegmentHeight: Dp = 38.dp

/**
 * The reader's presentation settings.
 *
 * A [Popup] hanging from the toolbar rather than a sheet: adjusting the theme or
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
    onDismissRequest: () -> Unit
) {
    // Animated through a graphicsLayer rather than AnimatedVisibility for the
    // same reason as the book actions menu: the latter measures its child at
    // zero size first, which on Android creates the popup window 0x0.
    val progress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(durationMillis = if (expanded) 180 else 140),
        label = "readerSettingsPanel"
    )

    if (!expanded && progress == 0f) return

    val positionProvider = remember { ReaderSettingsPositionProvider() }

    DismissOnBack(enabled = expanded, onDismiss = onDismissRequest)

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = false)
    ) {
        GlassSurface(
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.graphicsLayer {
                alpha = progress
                val scale = 0.92f + 0.08f * progress
                scaleX = scale
                scaleY = scale
                transformOrigin = PanelTransformOrigin
            }
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
                    SegmentedOptions(
                        options = ReaderTheme.entries,
                        selected = settings.theme,
                        label = { it.name },
                        onSelect = { onSettingsChange(settings.copy(theme = it)) }
                    )
                }

                SettingGroup(label = "Reading width") {
                    SegmentedOptions(
                        options = ReadingWidth.entries,
                        selected = settings.readingWidth,
                        label = { it.name },
                        onSelect = { onSettingsChange(settings.copy(readingWidth = it)) }
                    )
                }

                PanelDivider()

                SettingGroup(label = "Text size", enabled = typographyEnabled) {
                    TextSizeStepper(
                        settings = settings,
                        enabled = typographyEnabled,
                        onSettingsChange = onSettingsChange
                    )
                }

                SettingGroup(label = "Line spacing", enabled = typographyEnabled) {
                    SegmentedOptions(
                        options = LineSpacing.entries,
                        selected = settings.lineSpacing,
                        label = { it.name },
                        enabled = typographyEnabled,
                        onSelect = { onSettingsChange(settings.copy(lineSpacing = it)) }
                    )
                }

                SettingGroup(label = "Typeface", enabled = typographyEnabled) {
                    SegmentedOptions(
                        options = ReaderTypeface.entries,
                        selected = settings.typeface,
                        label = { if (it == ReaderTypeface.Serif) "Serif" else "Sans" },
                        enabled = typographyEnabled,
                        onSelect = { onSettingsChange(settings.copy(typeface = it)) }
                    )
                }

                if (!typographyEnabled) {
                    Text(
                        text = "Text settings apply to reflowable books - this one " +
                            "carries its own fixed layout.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = PanelPadding, vertical = 4.dp)
                    )
                }
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
 * One choice from a short list, in the style of an iOS segmented control.
 *
 * Segments share the width evenly so the track stays the same size whatever the
 * labels are, and each carries radio-button semantics so assistive technology
 * reads them as a single choice rather than as unrelated buttons.
 */
@Composable
private fun <T> SegmentedOptions(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 1f else 0.5f)
            )
            .height(SegmentHeight)
            .padding(3.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selected

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent
                    )
                    .selectable(
                        selected = isSelected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(option) }
                    )
                    .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = if (enabled) 1f else DisabledAlpha
                    )
                )
            }
        }
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

/**
 * Back on Android, Escape on desktop - the popup is not focusable and cannot
 * route either itself. Matches how the book actions menu closes.
 */
@Suppress("DEPRECATION")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DismissOnBack(
    enabled: Boolean,
    onDismiss: () -> Unit
) {
    BackHandler(enabled = enabled, onBack = onDismiss)
}

/** Hangs the panel below the control it belongs to, clamped into the window. */
private class ReaderSettingsPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val x = if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.right - popupContentSize.width
        } else {
            anchorBounds.left
        }

        return IntOffset(
            x = x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y = anchorBounds.bottom
                .coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0))
        )
    }
}

private const val DisabledAlpha = 0.38f
