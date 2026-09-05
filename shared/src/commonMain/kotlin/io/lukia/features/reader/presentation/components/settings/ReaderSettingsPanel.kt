package io.lukia.features.reader.presentation.components.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import io.lukia.core.domain.reading.PdfLayout
import io.lukia.core.presentation.displayName
import io.lukia.features.reader.domain.FigureSize
import io.lukia.features.reader.domain.LineSpacing
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.domain.ReaderTheme
import io.lukia.features.reader.domain.ReaderTypeface
import io.lukia.features.reader.domain.ReadingWidth
import io.lukia.features.reader.domain.TextAlignment
import io.lukia.features.reader.presentation.displayName
import io.lukia.features.reader.presentation.theme.ReaderPanelWidth
import io.lukia.getPlatform
import io.lukia.resources.Res
import io.lukia.resources.reader_keep_screen_awake
import io.lukia.resources.reader_note_fixed_layout
import io.lukia.resources.reader_note_fixed_layout_switchable
import io.lukia.resources.reader_note_pdf_as_text
import io.lukia.resources.reader_section_keyboard
import io.lukia.resources.reader_section_page
import io.lukia.resources.reader_section_text_and_images
import io.lukia.resources.reader_setting_alignment
import io.lukia.resources.reader_setting_image_size
import io.lukia.resources.reader_setting_layout
import io.lukia.resources.reader_setting_line_spacing
import io.lukia.resources.reader_setting_page_width
import io.lukia.resources.reader_setting_text_size
import io.lukia.resources.reader_setting_theme
import io.lukia.resources.reader_setting_typeface
import io.lukia.shared.presentation.components.controls.SegmentedControl
import io.lukia.shared.presentation.components.menu.DismissOnBack
import io.lukia.shared.presentation.components.menu.popoverTransition
import io.lukia.shared.presentation.components.menu.rememberPopoverProgress
import io.lukia.shared.presentation.components.surface.GlassSurface
import io.lukia.shared.presentation.components.surface.GlassTone
import io.lukia.shared.presentation.components.surface.LukiaDivider
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import io.lukia.shared.presentation.theme.isWideWindow
import org.jetbrains.compose.resources.stringResource

/** Grows out of the control it hangs from, matching the book actions menu. */
private val PanelTransformOrigin = TransformOrigin(1f, 0f)

/**
 * How far the panel may grow before it scrolls inside itself.
 *
 * The compact one is the taller of the two because it is the one whose controls
 * are stacked under their labels, and because a phone has nothing else on screen
 * to leave room for. Either way the window is the real limit - a phone in
 * landscape stops the panel long before this does.
 */
private val CompactPanelMaxHeight: Dp = 560.dp
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
 *
 * [translationSection] is translating, at the top of the panel: the language and
 * the gestures on every window, and on a phone - whose toolbar has no room for
 * them - the controls a wider window's toolbar carries as glyphs. Empty when
 * nothing is set up to translate with, so the panel neither knows nor cares what
 * translating is.
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
    /**
     * Whether this device is one that dims and locks itself, which is the only
     * kind the screen setting means anything on. Read from the platform rather
     * than passed by the screen, in the way the theme reads whether the app is
     * being touched or pointed at.
     */
    keepAwakeOffered: Boolean = getPlatform().isMobile,
    translationSection: @Composable () -> Unit = {},
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
                // A phone gets the width of its window rather than a column
                // floating in one corner of it. The panel is the only thing on
                // screen a thumb is aiming at while it is open, and five width
                // presets sharing 284dp were a row of 56dp targets against the
                // 44dp a finger is actually worth.
                .then(if (wide) Modifier.width(ReaderPanelWidth) else Modifier.fillMaxWidth())
                // A short window - a phone in landscape - would otherwise
                // leave the last setting past the bottom edge with no way to
                // reach it.
                .heightIn(max = if (wide) WidePanelMaxHeight else CompactPanelMaxHeight)
                .verticalScroll(rememberScrollState())
                .padding(vertical = Space.md),
        ) {
            // Empty until there is something set up to translate with.
            translationSection()

            SectionHeading(
                label = stringResource(Res.string.reader_section_page),
                wide = wide,
                inset = inset,
            )

            // First, because it is the one setting here that changes what the
            // reader is looking at rather than how it looks: everything below
            // acts on the book this chooses.
            if (layout != null) {
                SettingGroup(
                    label = stringResource(Res.string.reader_setting_layout),
                    wide = wide,
                    inset = inset,
                ) {
                    SegmentedControl(
                        options = PdfLayout.entries,
                        selected = layout,
                        label = { it.displayName() },
                        onSelect = onLayoutChange,
                    )
                }

                if (layout.asText) {
                    PanelNote(
                        text = stringResource(Res.string.reader_note_pdf_as_text),
                        inset = inset,
                    )
                }
            }

            SettingGroup(
                label = stringResource(Res.string.reader_setting_theme),
                wide = wide,
                inset = inset,
            ) {
                SegmentedControl(
                    options = ReaderTheme.entries,
                    selected = settings.theme,
                    label = { it.displayName() },
                    onSelect = { onSettingsChange(settings.copy(theme = it)) },
                )
            }

            // Shown as percentages rather than names: with five steps that
            // run past the width of the window, "125%" says what "Larger"
            // cannot.
            SettingGroup(
                label = stringResource(Res.string.reader_setting_page_width),
                wide = wide,
                inset = inset,
            ) {
                SegmentedControl(
                    options = ReadingWidth.entries,
                    // Nothing is selected when a pinch has left the page
                    // between two presets, which is the honest answer.
                    selected = settings.matchingWidth,
                    label = { it.displayName() },
                    onSelect = { onSettingsChange(settings.withPageScale(it.fraction)) },
                )
            }

            // Last of the page settings, and the only one here that is about the
            // device rather than the book: everything above changes what the
            // page looks like, and this one keeps it visible long enough to
            // read. Absent altogether where a screen does not lock itself.
            if (keepAwakeOffered) {
                PanelSwitchRow(
                    label = stringResource(Res.string.reader_keep_screen_awake),
                    checked = settings.keepScreenAwake,
                    onChange = { onSettingsChange(settings.copy(keepScreenAwake = it)) },
                    inset = inset,
                )
            }

            LukiaDivider(
                inset = inset,
                endInset = inset,
                modifier = Modifier.padding(vertical = Space.md),
            )

            SectionHeading(
                label = stringResource(Res.string.reader_section_text_and_images),
                wide = wide,
                inset = inset,
                enabled = typographyEnabled,
            )

            // Placed before the controls it explains, so it is read first
            // whether by eye or by screen reader.
            if (!typographyEnabled) {
                PanelNote(
                    text = stringResource(
                        if (layout == null) {
                            Res.string.reader_note_fixed_layout
                        } else {
                            Res.string.reader_note_fixed_layout_switchable
                        },
                    ),
                    inset = inset,
                )
            }

            SettingGroup(
                label = stringResource(Res.string.reader_setting_text_size),
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
                label = stringResource(Res.string.reader_setting_line_spacing),
                wide = wide,
                inset = inset,
                enabled = typographyEnabled,
            ) {
                SegmentedControl(
                    options = LineSpacing.entries,
                    selected = settings.lineSpacing,
                    label = { it.displayName() },
                    enabled = typographyEnabled,
                    onSelect = { onSettingsChange(settings.copy(lineSpacing = it)) },
                )
            }

            SettingGroup(
                label = stringResource(Res.string.reader_setting_alignment),
                wide = wide,
                inset = inset,
                enabled = typographyEnabled,
            ) {
                SegmentedControl(
                    options = TextAlignment.entries,
                    selected = settings.alignment,
                    label = { it.displayName() },
                    enabled = typographyEnabled,
                    onSelect = { onSettingsChange(settings.copy(alignment = it)) },
                )
            }

            SettingGroup(
                label = stringResource(Res.string.reader_setting_typeface),
                wide = wide,
                inset = inset,
                enabled = typographyEnabled,
            ) {
                SegmentedControl(
                    options = ReaderTypeface.entries,
                    selected = settings.typeface,
                    label = { it.displayName() },
                    enabled = typographyEnabled,
                    onSelect = { onSettingsChange(settings.copy(typeface = it)) },
                )
            }

            // Last of the reflowable settings: it is the one somebody reaches
            // for after the type is comfortable and a diagram still is not.
            SettingGroup(
                label = stringResource(Res.string.reader_setting_image_size),
                wide = wide,
                inset = inset,
                enabled = typographyEnabled,
            ) {
                SegmentedControl(
                    options = FigureSize.entries,
                    selected = settings.figureSize,
                    label = { it.displayName() },
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
                SectionHeading(
                    label = stringResource(Res.string.reader_section_keyboard),
                    wide = true,
                    inset = inset,
                )
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
