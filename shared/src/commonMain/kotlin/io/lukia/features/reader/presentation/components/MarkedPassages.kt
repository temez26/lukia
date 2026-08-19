package io.lukia.features.reader.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.lukia.features.translate.domain.Sentence
import io.lukia.features.translate.domain.Word
import io.lukia.features.translate.domain.words
import io.lukia.features.translate.presentation.SentenceTranslations

/**
 * Which passages of this paragraph are marked, and how - named by index rather
 * than by where they ended up on the page.
 *
 * By index because two settings of the paragraph are on screen at once while a
 * translation fades in, and a mark has to be placed against whichever of them it
 * is being drawn over. What is the same for both is *which* sentence and which
 * word are marked; where those sit is each copy's own business, which is what
 * [marksFor] settles.
 *
 * A passage nobody has touched is left out entirely, which is what makes an
 * untranslated paragraph cost nothing to draw.
 */
internal class MarkedPassages(
    private val sentences: Map<Int, PassageMarkStyle>,
    private val words: Map<Pair<Int, Int>, PassageMarkStyle>,
) {

    val anyWaiting: Boolean = sentences.values.any { it == PassageMarkStyle.Waiting } ||
        words.values.any { it == PassageMarkStyle.Waiting }

    private val isEmpty: Boolean get() = sentences.isEmpty() && words.isEmpty()

    fun marksFor(text: ParagraphText): List<PassageMark> {
        if (isEmpty) return emptyList()

        return buildList {
            text.sentences.forEach { span ->
                sentences[span.index]?.let { add(PassageMark(span.start, span.end, it)) }
            }

            text.words.forEach { word ->
                words[word.sentenceIndex to word.span.index]?.let {
                    add(PassageMark(word.span.start, word.span.end, it))
                }
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        other is MarkedPassages && sentences == other.sentences && words == other.words

    override fun hashCode(): Int = 31 * sentences.hashCode() + words.hashCode()
}

@Composable
internal fun passageMarkStyles(
    sentences: List<Sentence>,
    words: List<Word>,
    translations: SentenceTranslations,
): MarkedPassages = remember(sentences, words, translations) {
    MarkedPassages(
        sentences = buildMap {
            // A paragraph translated whole is drawn as one span under this index,
            // so its mark is filed under the same one - see WHOLE_BLOCK.
            translations.block.markStyle()?.let { put(WHOLE_BLOCK, it) }

            sentences.forEach { sentence ->
                translations[sentence.index].markStyle()?.let { put(sentence.index, it) }
            }
        },
        words = buildMap {
            words.forEach { word ->
                translations.words(word.sentenceIndex)[word.index].markStyle()?.let {
                    put(word.sentenceIndex to word.index, it)
                }
            }
        },
    )
}
