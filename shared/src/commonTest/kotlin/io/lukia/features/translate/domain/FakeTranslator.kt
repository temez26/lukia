package io.lukia.features.translate.domain

/**
 * A translator that does not translate.
 *
 * It answers with the passage marked up rather than with the passage unchanged,
 * so a test that accidentally asserts on the wrong string fails rather than
 * passing on the original. The note repeats what the book said about itself,
 * which is the cheapest way for a test to see that the title and the genre
 * actually reached whatever was asked to translate.
 */
class FakeTranslator : Translator {

    override suspend fun translate(
        request: TranslationRequest,
        onPartial: (String) -> Unit
    ): Translation = Translation(
        text = "[${request.targetLanguage.englishName}] ${request.text}",
        note = request.context.describe()
    )
}

private fun TranslationContext.describe(): String? {
    val known = listOfNotNull(bookTitle, authors.firstOrNull(), genres.firstOrNull(), chapterTitle)

    return known.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
