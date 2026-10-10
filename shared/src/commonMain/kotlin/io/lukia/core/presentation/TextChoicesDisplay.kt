package io.lukia.core.presentation

import androidx.compose.runtime.Composable
import io.lukia.core.domain.text.PassageUnit
import io.lukia.resources.Res
import io.lukia.resources.passage_unit_paragraph
import io.lukia.resources.passage_unit_sentence
import io.lukia.resources.passage_unit_word
import org.jetbrains.compose.resources.stringResource

/**
 * How much of the page a question is about, in words.
 *
 * In `core` beside [PassageUnit] rather than with the translator, for the same
 * reason [PdfLayout.displayName] is: the reader's own panel and the settings
 * screen both offer this choice, and two tables would be two chances to disagree
 * about what a paragraph is called.
 */
@Composable
fun PassageUnit.displayName(): String = when (this) {
    PassageUnit.Word -> stringResource(Res.string.passage_unit_word)
    PassageUnit.Sentence -> stringResource(Res.string.passage_unit_sentence)
    PassageUnit.Paragraph -> stringResource(Res.string.passage_unit_paragraph)
}
