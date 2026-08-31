package io.lukia.features.reader.presentation.components.translation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import io.lukia.core.presentation.displayShortMessage
import io.lukia.features.translate.domain.text.Sentence
import io.lukia.features.translate.domain.text.Word
import io.lukia.features.translate.domain.text.words
import io.lukia.features.translate.presentation.PassageTranslation
import io.lukia.features.translate.presentation.SentenceTranslations
import io.lukia.features.translate.presentation.shownText

/** A note is an aside about the text, and is set smaller than the text. */
internal const val NOTE_SCALE = 0.82f

/**
 * What the translator had to say about this paragraph, under it.
 *
 * Two different things share this line, because they belong in the same place:
 * a remark about an ambiguous word, and the reason a passage could not be
 * translated at all. Under rather than beside, because a note set into the prose
 * is a footnote in the middle of a sentence and the reader is here to read the
 * book.
 *
 * A failure has to appear somewhere. Marked only by a pair of dots it says
 * nothing about what went wrong, which leaves somebody looking at a mark and
 * wondering whether anything is happening.
 *
 * Single words have both as much as sentences do - a word asked about on its own
 * is usually the ambiguous one, so its note is the one most worth reading - and
 * they are gathered in reading order with the sentences they sit in rather than
 * in a list of their own.
 */
@Composable
internal fun TranslatorsRemarks(
    sentences: List<Sentence>,
    words: List<Word>,
    translations: SentenceTranslations,
    style: TextStyle,
) {
    val remarks = remember(sentences, words, translations) {
        buildList {
            translations.block.remark()?.let { add(it) }

            // Nothing inside a paragraph translated whole is on the page, so
            // nothing inside it has anything to remark on.
            if (translations.block.shownText != null) return@buildList

            sentences.forEach { sentence ->
                translations[sentence.index].remark()?.let { add(it) }

                // A word is only on the page while its sentence's own words are,
                // and a remark about something nobody can see is noise.
                if (translations[sentence.index].shownText != null) return@forEach

                words.forEach { word ->
                    if (word.sentenceIndex != sentence.index) return@forEach

                    translations.words(sentence.index)[word.index]
                        .remark(replaced = word.text)
                        ?.let { add(it) }
                }
            }
        }
    }

    if (remarks.isEmpty()) return

    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(top = 6.dp),
    ) {
        remarks.forEach { remark ->
            Text(
                text = remark.text,
                style = style.copy(
                    fontSize = style.fontSize * NOTE_SCALE,
                    lineHeight = style.fontSize * NOTE_SCALE * 1.35f,
                    fontStyle = if (remark.failed) FontStyle.Normal else FontStyle.Italic,
                    color = if (remark.failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ),
            )
        }
    }
}

/**
 * What is worth saying under the paragraph about one passage, if anything.
 *
 * [replaced] is the book's own word, named for a note about a single word
 * because that word is no longer on the page - the translation is standing where
 * it was, and a remark about "piece" under a paragraph now reading "ase" is a
 * remark about nothing the reader can see. A failure needs no such introduction:
 * nothing was replaced, so the word is still there to look at.
 */
private fun PassageTranslation?.remark(replaced: String? = null): Remark? = when (this) {
    null, is PassageTranslation.Loading -> null

    is PassageTranslation.Failed -> Remark(error.displayShortMessage(), failed = true)

    is PassageTranslation.Ready -> takeIf { !showingOriginal }
        ?.translation
        ?.note
        ?.let { note ->
            Remark(if (replaced == null) note else "$replaced: $note", failed = false)
        }
}

private data class Remark(val text: String, val failed: Boolean)
