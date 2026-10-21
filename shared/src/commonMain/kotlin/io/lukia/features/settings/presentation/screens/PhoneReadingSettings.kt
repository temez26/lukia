package io.lukia.features.settings.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import io.lukia.features.reader.presentation.components.settings.PanelDisclosureRow
import io.lukia.features.reader.presentation.components.settings.TextSizeStepper
import io.lukia.features.reader.presentation.displayName
import io.lukia.features.reader.presentation.theme.ReaderPageTheming
import io.lukia.features.reader.presentation.theme.readerPageStyle
import io.lukia.features.reader.presentation.theme.readerTextStyle
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
import io.lukia.resources.settings_percent
import io.lukia.resources.settings_reading_layout
import io.lukia.resources.settings_reading_sample
import io.lukia.resources.settings_text_and_images_footnote
import io.lukia.resources.settings_the_page_footnote
import io.lukia.shared.presentation.components.controls.ChoiceDensity
import io.lukia.shared.presentation.components.controls.ChoiceList
import io.lukia.shared.presentation.components.surface.GroupSection
import io.lukia.shared.presentation.theme.HairlineWidth
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import io.lukia.shared.presentation.theme.disclosureIn
import io.lukia.shared.presentation.theme.disclosureOut
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val PERCENT = 100

/**
 * How a book is set, laid out for a phone: what it looks like first, then the
 * three settings that change it most as things to look at, then the rest as
 * rows that open where they are.
 *
 * Seven segmented controls stacked one on another - the window's layout - said
 * nothing about what any of them did until a book was opened. Here the sample
 * at the top is set by the same code that sets a book, so each change shows
 * before it is read about. Kindle and Apple Books open their own type settings
 * the same way.
 */
@Composable
internal fun PhoneReadingSettings(
    reader: ReaderSettings,
    onReaderChange: (ReaderSettings) -> Unit,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalLukiaDimens.current

    Column(
        verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing),
        modifier = modifier.fillMaxWidth(),
    ) {
        GroupSection(
            title = null,
            color = groupColor,
            footnote = stringResource(Res.string.settings_the_page_footnote),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xl)) {
                ReadingSample(reader)

                LabelledField(label = stringResource(Res.string.reader_setting_theme)) {
                    TileRow {
                        ReaderTheme.entries.forEach { theme ->
                            ThemeTile(
                                theme = theme,
                                selected = theme == reader.theme,
                                onSelect = { onReaderChange(reader.copy(theme = theme)) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                LabelledField(label = stringResource(Res.string.reader_setting_text_size)) {
                    TextSizeStepper(settings = reader, enabled = true, onSettingsChange = onReaderChange)
                }

                LabelledField(label = stringResource(Res.string.reader_setting_typeface)) {
                    TileRow {
                        ReaderTypeface.entries.forEach { typeface ->
                            TypefaceTile(
                                typeface = typeface,
                                selected = typeface == reader.typeface,
                                onSelect = { onReaderChange(reader.copy(typeface = typeface)) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        LayoutSection(reader = reader, onReaderChange = onReaderChange, groupColor = groupColor)
    }
}

/**
 * A few lines set exactly as a book would be: the reader's page colours and the
 * reader's text style, from the same functions the reader itself calls.
 */
@Composable
private fun ReadingSample(reader: ReaderSettings) {
    ReaderPageTheming(theme = reader.theme) {
        Text(
            text = stringResource(Res.string.settings_reading_sample),
            style = readerTextStyle(reader),
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(readerPageStyle(reader.theme).pageColor)
                .border(HairlineWidth, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                .padding(Space.xl),
        )
    }
}

/** The settings changed once and left: each a row that opens its choices in place. */
@Composable
private fun LayoutSection(
    reader: ReaderSettings,
    onReaderChange: (ReaderSettings) -> Unit,
    groupColor: Color,
) {
    // One open at a time, so opening the next closes the last instead of the
    // card growing a list for every row touched.
    var open by rememberSaveable { mutableStateOf<String?>(null) }

    SettingsSection(
        title = stringResource(Res.string.settings_reading_layout),
        info = stringResource(Res.string.settings_text_and_images_footnote),
        groupColor = groupColor,
        padding = Space.xs,
    ) {
        Column {
            ChoiceRow(
                label = stringResource(Res.string.reader_setting_page_width),
                options = ReadingWidth.entries,
                // Nothing is selected when a pinch in the reader has left the
                // page between two presets; the row then says what it is.
                selected = reader.matchingWidth,
                value = reader.matchingWidth?.displayName()
                    ?: stringResource(Res.string.settings_percent, (reader.pageScale * PERCENT).roundToInt()),
                display = { it.displayName() },
                onSelect = { onReaderChange(reader.withPageScale(it.fraction)) },
                open = open,
                onOpen = { open = it },
            )
            ChoiceRow(
                label = stringResource(Res.string.reader_setting_line_spacing),
                options = LineSpacing.entries,
                selected = reader.lineSpacing,
                display = { it.displayName() },
                onSelect = { onReaderChange(reader.copy(lineSpacing = it)) },
                open = open,
                onOpen = { open = it },
            )
            ChoiceRow(
                label = stringResource(Res.string.reader_setting_alignment),
                options = TextAlignment.entries,
                selected = reader.alignment,
                display = { it.displayName() },
                onSelect = { onReaderChange(reader.copy(alignment = it)) },
                open = open,
                onOpen = { open = it },
            )
            ChoiceRow(
                label = stringResource(Res.string.reader_setting_image_size),
                options = FigureSize.entries,
                selected = reader.figureSize,
                display = { it.displayName() },
                onSelect = { onReaderChange(reader.copy(figureSize = it)) },
                open = open,
                onOpen = { open = it },
            )
            PdfRow(reader = reader, onReaderChange = onReaderChange, open = open, onOpen = { open = it })
        }
    }
}

/** What a PDF nobody has chosen for opens as, with what each choice does to it said under it. */
@Composable
private fun PdfRow(
    reader: ReaderSettings,
    onReaderChange: (ReaderSettings) -> Unit,
    open: String?,
    onOpen: (String?) -> Unit,
) {
    ChoiceRow(
        label = stringResource(Res.string.settings_opening_a_pdf),
        options = PdfLayout.entries,
        selected = reader.defaultPdfLayout,
        display = { it.displayName() },
        onSelect = { onReaderChange(reader.copy(defaultPdfLayout = it)) },
        open = open,
        onOpen = onOpen,
        detail = {
            stringResource(
                if (it.asText) {
                    Res.string.settings_opening_a_pdf_as_text
                } else {
                    Res.string.settings_opening_a_pdf_as_pages
                },
            )
        },
    )
}

/**
 * A setting's name and value, which opens its choices under it. The reader's
 * own panel row, so a setting looks the same in the panel and here.
 */
@Composable
private fun <T> ChoiceRow(
    label: String,
    options: List<T>,
    selected: T?,
    display: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    open: String?,
    onOpen: (String?) -> Unit,
    value: String? = null,
    detail: @Composable (T) -> String? = { null },
) {
    val expanded = open == label

    PanelDisclosureRow(
        label = label,
        value = value ?: selected?.let { display(it) }.orEmpty(),
        expanded = expanded,
        onClick = { onOpen(if (expanded) null else label) },
        inset = Space.xs,
    )

    AnimatedVisibility(visible = expanded, enter = disclosureIn(), exit = disclosureOut()) {
        ChoiceList(
            options = options,
            selected = selected,
            label = display,
            detail = detail,
            onSelect = {
                onSelect(it)
                onOpen(null)
            },
            density = ChoiceDensity.Compact,
        )
    }
}
