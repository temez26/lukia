package io.lukia.features.reader.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.reader.domain.FigureSize
import io.lukia.features.reader.domain.LineSpacing
import io.lukia.features.reader.domain.PdfLayout
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.domain.ReaderTheme
import io.lukia.features.reader.domain.ReaderTypeface
import io.lukia.features.reader.domain.ReadingWidth
import io.lukia.features.reader.domain.TextAlignment
import io.lukia.shared.presentation.components.DismissOnBack
import io.lukia.shared.presentation.components.GlassSurface
import io.lukia.shared.presentation.components.GlassTone
import io.lukia.shared.presentation.components.LukiaDivider
import io.lukia.shared.presentation.components.SegmentedControl
import io.lukia.shared.presentation.components.popoverTransition
import io.lukia.shared.presentation.components.rememberPopoverProgress
import io.lukia.shared.presentation.theme.LocalLukiaDimens
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
 *
 * [layout] is null for every document that has no choice to make about how it is
 * read, which is every format except PDF. The control is left out altogether
 * there rather than shown disabled: unlike the typography settings, which a PDF
 * really does have and cannot use, this is a question that was never asked of an
 * EPUB.
 */
@Composable
internal fun ReaderSettingsPanel(
    expanded: Boolean,
    settings: ReaderSettings,
    typographyEnabled: Boolean,
    layout: PdfLayout?,
    onSettingsChange: (ReaderSettings) -> Unit,
    onLayoutChange: (PdfLayout) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = rememberPopoverProgress(expanded, label = "readerSettingsPanel")

    if (!expanded && progress == 0f) return

    DismissOnBack(enabled = expanded, onDismiss = onDismissRequest)

    val wide = isWideWindow
    val inset = LocalLukiaDimens.current.panelPadding

    GlassSurface(
        shape = MaterialTheme.shapes.large,
        tone = GlassTone.Panel,
        modifier = modifier.popoverTransition(progress, PanelTransformOrigin),
    ) {
        Column(
            modifier = Modifier
                .width(if (wide) WidePanelWidth else CompactPanelWidth)
                // A short window - a phone in landscape - would otherwise
                // leave the last setting past the bottom edge with no way to
                // reach it.
                .heightIn(max = if (wide) WidePanelMaxHeight else CompactPanelMaxHeight)
                .verticalScroll(rememberScrollState())
                .padding(vertical = Space.md),
        ) {
            SectionHeading(label = "Page", wide = wide, inset = inset)

            // First, because it is the one setting here that changes what the
            // reader is looking at rather than how it looks: everything below
            // acts on the book this chooses.
            if (layout != null) {
                SettingGroup(label = "Layout", wide = wide, inset = inset) {
                    SegmentedControl(
                        options = PdfLayout.entries,
                        selected = layout,
                        label = { it.name },
                        onSelect = onLayoutChange,
                    )
                }

                if (layout.asText) {
                    PanelNote(
                        text = "The words and pictures of the PDF, re-set by this " +
                            "app. Its own page layout is not kept.",
                        inset = inset,
                    )
                }
            }

            SettingGroup(label = "Theme", wide = wide, inset = inset) {
                SegmentedControl(
                    options = ReaderTheme.entries,
                    selected = settings.theme,
                    label = { it.name },
                    onSelect = { onSettingsChange(settings.copy(theme = it)) },
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
                    onSelect = { onSettingsChange(settings.withPageScale(it.fraction)) },
                )
            }

            LukiaDivider(
                inset = inset,
                endInset = inset,
                modifier = Modifier.padding(vertical = Space.md),
            )

            SectionHeading(
                label = "Text and images",
                wide = wide,
                inset = inset,
                enabled = typographyEnabled,
            )

            // Placed before the controls it explains, so it is read first
            // whether by eye or by screen reader.
            if (!typographyEnabled) {
                PanelNote(
                    text = "These apply to reflowable books - this one carries " +
                        "its own fixed layout." +
                        if (layout == null) "" else " Read it as text to use them.",
                    inset = inset,
                )
            }

            SettingGroup(
                label = "Text size",
                wide = wide,
                inset = inset,
                enabled = typographyEnabled,
            ) {
                TextSizeStepper(
                    settings = settings,
                    enabled = typographyEnabled,
                    onSettingsChange = onSettingsChange,
                )
            }

            SettingGroup(
                label = "Line spacing",
                wide = wide,
                inset = inset,
                enabled = typographyEnabled,
            ) {
                SegmentedControl(
                    options = LineSpacing.entries,
                    selected = settings.lineSpacing,
                    label = { it.name },
                    enabled = typographyEnabled,
                    onSelect = { onSettingsChange(settings.copy(lineSpacing = it)) },
                )
            }

            SettingGroup(
                label = "Alignment",
                wide = wide,
                inset = inset,
                enabled = typographyEnabled,
            ) {
                SegmentedControl(
                    options = TextAlignment.entries,
                    selected = settings.alignment,
                    label = { it.name },
                    enabled = typographyEnabled,
                    onSelect = { onSettingsChange(settings.copy(alignment = it)) },
                )
            }

            SettingGroup(
                label = "Typeface",
                wide = wide,
                inset = inset,
                enabled = typographyEnabled,
            ) {
                SegmentedControl(
                    options = ReaderTypeface.entries,
                    selected = settings.typeface,
                    label = { if (it == ReaderTypeface.Serif) "Serif" else "Sans" },
                    enabled = typographyEnabled,
                    onSelect = { onSettingsChange(settings.copy(typeface = it)) },
                )
            }

            // Last of the reflowable settings: it is the one somebody reaches
            // for after the type is comfortable and a diagram still is not.
            SettingGroup(
                label = "Image size",
                wide = wide,
                inset = inset,
                enabled = typographyEnabled,
            ) {
                SegmentedControl(
                    options = FigureSize.entries,
                    selected = settings.figureSize,
                    label = { it.name },
                    enabled = typographyEnabled,
                    onSelect = { onSettingsChange(settings.copy(figureSize = it)) },
                )
            }

            if (wide) {
                LukiaDivider(
                    inset = inset,
                    endInset = inset,
                    modifier = Modifier.padding(vertical = Space.md),
                )
                SectionHeading(label = "Keyboard", wide = true, inset = inset)
                ShortcutList(reflowable = typographyEnabled, inset = inset)
            }
        }
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
    modifier: Modifier = Modifier,
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Text(
            text = "Aa",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Serif,
            color = tint,
            maxLines = 1,
            modifier = Modifier.wrapContentSize(unbounded = true),
        )
    }
}
