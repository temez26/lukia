package io.lukia.features.settings.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.lukia.core.domain.reading.PdfLayout
import io.lukia.core.presentation.displayName
import io.lukia.features.reader.domain.FigureSize
import io.lukia.features.reader.domain.LineSpacing
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.domain.ReaderTheme
import io.lukia.features.reader.domain.ReaderTypeface
import io.lukia.features.reader.domain.ReadingWidth
import io.lukia.features.reader.domain.TextAlignment
import io.lukia.features.reader.presentation.components.settings.TextSizeStepper
import io.lukia.features.reader.presentation.displayName
import io.lukia.resources.Res
import io.lukia.resources.reader_setting_alignment
import io.lukia.resources.reader_setting_image_size
import io.lukia.resources.reader_setting_line_spacing
import io.lukia.resources.reader_setting_page_width
import io.lukia.resources.reader_setting_text_size
import io.lukia.resources.reader_setting_theme
import io.lukia.resources.reader_setting_typeface
import io.lukia.resources.settings_opening_a_pdf
import io.lukia.resources.settings_opening_a_pdf_as_pages
import io.lukia.resources.settings_opening_a_pdf_as_text
import io.lukia.resources.settings_text_and_images
import io.lukia.resources.settings_text_and_images_footnote
import io.lukia.resources.settings_the_page
import io.lukia.resources.settings_the_page_footnote
import io.lukia.shared.presentation.components.controls.SegmentedControl
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/**
 * How a book is set: the surface it is drawn on, the type it is set in, and what
 * a PDF is opened as before anybody has said.
 *
 * The same settings the reader's own panel holds, and deliberately so - there is
 * one set of them, and this is where they are reached without a book open. The
 * panel is for the reader who can see what a change did; this is for the reader
 * who has decided how they like their books and wants to say so once.
 *
 * Grouped as the panel groups them, in the panel's order, so that somebody who
 * knows one page can find a setting on the other.
 */
@Composable
internal fun ReadingSettings(
    reader: ReaderSettings,
    onReaderChange: (ReaderSettings) -> Unit,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    if (LocalPhoneSettingsPage.current != null) {
        PhoneReadingSettings(reader, onReaderChange, groupColor, modifier)
        return
    }

    val dimens = LocalLukiaDimens.current

    Column(modifier = modifier.fillMaxWidth()) {
        PageSection(reader = reader, onReaderChange = onReaderChange, groupColor = groupColor)

        TypeSection(
            reader = reader,
            onReaderChange = onReaderChange,
            groupColor = groupColor,
            modifier = Modifier.padding(top = dimens.sectionSpacing),
        )

        PdfSection(
            reader = reader,
            onReaderChange = onReaderChange,
            groupColor = groupColor,
            modifier = Modifier.padding(top = dimens.sectionSpacing),
        )
    }
}

/** The surface a book is drawn on, and how much of the window it is given. */
@Composable
private fun PageSection(
    reader: ReaderSettings,
    onReaderChange: (ReaderSettings) -> Unit,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    SettingsSection(
        title = stringResource(Res.string.settings_the_page),
        info = stringResource(Res.string.settings_the_page_footnote),
        groupColor = groupColor,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            LabelledField(label = stringResource(Res.string.reader_setting_theme)) {
                SegmentedControl(
                    options = ReaderTheme.entries,
                    selected = reader.theme,
                    label = { it.displayName() },
                    onSelect = { onReaderChange(reader.copy(theme = it)) },
                )
            }

            // Percentages rather than names, as in the panel: the last two run
            // past the width of the window, and "125%" says what "Larger" cannot.
            LabelledField(label = stringResource(Res.string.reader_setting_page_width)) {
                SegmentedControl(
                    options = ReadingWidth.entries,
                    // Nothing is selected when a pinch in the reader has left the
                    // page between two presets, which is the honest answer.
                    selected = reader.matchingWidth,
                    label = { it.displayName() },
                    onSelect = { onReaderChange(reader.withPageScale(it.fraction)) },
                )
            }
        }
    }
}

/**
 * The settings a reflowable book has and a fixed one has not.
 *
 * Not disabled here the way the panel disables them: the panel is open beside a
 * particular book and can say whether these reach it, while this page is about
 * every book and cannot. The footnote says so instead.
 */
@Composable
private fun TypeSection(
    reader: ReaderSettings,
    onReaderChange: (ReaderSettings) -> Unit,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    SettingsSection(
        title = stringResource(Res.string.settings_text_and_images),
        info = stringResource(Res.string.settings_text_and_images_footnote),
        groupColor = groupColor,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            // The reader's own stepper rather than a second one that behaves
            // almost like it. Text size is the setting most often changed and the
            // one people recognise by shape, and two of them drifting apart would
            // be worse than this page reaching across to the reader for it.
            LabelledField(label = stringResource(Res.string.reader_setting_text_size)) {
                TextSizeStepper(
                    settings = reader,
                    enabled = true,
                    onSettingsChange = onReaderChange,
                )
            }

            LabelledField(label = stringResource(Res.string.reader_setting_line_spacing)) {
                SegmentedControl(
                    options = LineSpacing.entries,
                    selected = reader.lineSpacing,
                    label = { it.displayName() },
                    onSelect = { onReaderChange(reader.copy(lineSpacing = it)) },
                )
            }

            LabelledField(label = stringResource(Res.string.reader_setting_alignment)) {
                SegmentedControl(
                    options = TextAlignment.entries,
                    selected = reader.alignment,
                    label = { it.displayName() },
                    onSelect = { onReaderChange(reader.copy(alignment = it)) },
                )
            }

            LabelledField(label = stringResource(Res.string.reader_setting_typeface)) {
                SegmentedControl(
                    options = ReaderTypeface.entries,
                    selected = reader.typeface,
                    label = { it.displayName() },
                    onSelect = { onReaderChange(reader.copy(typeface = it)) },
                )
            }

            LabelledField(label = stringResource(Res.string.reader_setting_image_size)) {
                SegmentedControl(
                    options = FigureSize.entries,
                    selected = reader.figureSize,
                    label = { it.displayName() },
                    onSelect = { onReaderChange(reader.copy(figureSize = it)) },
                )
            }
        }
    }
}

/** What a PDF nobody has chosen for is opened as. */
@Composable
private fun PdfSection(
    reader: ReaderSettings,
    onReaderChange: (ReaderSettings) -> Unit,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    SettingsSection(
        title = stringResource(Res.string.settings_opening_a_pdf),
        info = stringResource(
            if (reader.defaultPdfLayout.asText) {
                Res.string.settings_opening_a_pdf_as_text
            } else {
                Res.string.settings_opening_a_pdf_as_pages
            },
        ),
        groupColor = groupColor,
        modifier = modifier,
    ) {
        SegmentedControl(
            options = PdfLayout.entries,
            selected = reader.defaultPdfLayout,
            label = { it.displayName() },
            onSelect = { onReaderChange(reader.copy(defaultPdfLayout = it)) },
        )
    }
}
