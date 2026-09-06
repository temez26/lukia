package io.lukia.features.reader.presentation.translation

import io.lukia.core.presentation.displayTitle
import io.lukia.features.reader.domain.ReaderDocument
import io.lukia.features.reader.domain.outline.ReaderOutline
import io.lukia.features.reader.domain.outline.chapterCovering
import io.lukia.features.reader.domain.text.TextAnchor
import io.lukia.features.translate.domain.TranslationRequest
import io.lukia.features.translate.domain.asTranslationContext
import io.lukia.features.translate.domain.settings.TargetLanguage

/**
 * What the reader points at, as something a translator can be asked.
 *
 * The book is read here rather than at the tap because this is where it is
 * known: the screen has the words and where they sit, and only the open document
 * can say which book they came out of and what it is about. That is the whole of
 * how a title and a genre reach a translator.
 */
internal fun ReaderDocument.translationRequest(
    selection: PassageSelection,
    targetLanguage: TargetLanguage,
    outline: ReaderOutline,
): TranslationRequest = TranslationRequest(
    text = selection.passage,
    targetLanguage = targetLanguage,
    context = info.metadata.asTranslationContext(
        bookTitle = info.displayTitle(),
        chapterTitle = selection.chapterTitle ?: outline.chapterOf(selection.anchor),
        surroundingText = selection.surroundingText,
    ),
    unit = selection.unit,
)

/**
 * What a passage sits under, for one that carries no chapter of its own.
 *
 * A reflowable book's section usually names itself, so the screen already knows
 * and this is not asked. What does not name itself is a page of a PDF, and every
 * section of a PDF being read as text - the reader made those sections, one per
 * page of the file, and a page has no title. The book's own table of contents
 * does know, and it has been read already.
 *
 * Both anchors are looked up the same way because both are asking the same
 * question of the same list: an outline entry addresses a page in a fixed-layout
 * document and a section in a re-set one, which is exactly what the two anchors
 * count in.
 */
private fun ReaderOutline.chapterOf(anchor: TextAnchor): String? {
    val chapters = (this as? ReaderOutline.Chapters)?.entries ?: return null

    val index = when (anchor) {
        is TextAnchor.Page -> anchor.pageIndex
        is TextAnchor.Section -> anchor.sectionIndex
    }

    return chapters.chapterCovering(index)
}
