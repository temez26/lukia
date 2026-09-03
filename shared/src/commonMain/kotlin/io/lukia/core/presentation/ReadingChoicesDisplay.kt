package io.lukia.core.presentation

import androidx.compose.runtime.Composable
import io.lukia.core.domain.reading.PdfLayout
import io.lukia.resources.Res
import io.lukia.resources.pdf_layout_original
import io.lukia.resources.pdf_layout_text
import org.jetbrains.compose.resources.stringResource

/**
 * What the two ways of putting a PDF on screen are called.
 *
 * In `core` rather than beside the reader because both the reader's own settings
 * panel and the settings screen offer this choice, and two tables would be two
 * chances to disagree about what [PdfLayout.Text] is called.
 */
@Composable
fun PdfLayout.displayName(): String = when (this) {
    PdfLayout.Original -> stringResource(Res.string.pdf_layout_original)
    PdfLayout.Text -> stringResource(Res.string.pdf_layout_text)
}
