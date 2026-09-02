package io.lukia.features.reader.presentation.translation

import io.lukia.core.domain.toAppError
import io.lukia.core.presentation.UiState
import io.lukia.features.translate.application.LookUpWordUseCase
import io.lukia.features.translate.domain.lookup.urlFor
import io.lukia.features.translate.domain.settings.TranslationSettings
import io.lukia.features.translate.domain.text.TextAnchor
import io.lukia.features.translate.infrastructure.TranslationSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The one word the reader is currently asking about, and nothing else.
 *
 * Apart from [ReaderTranslations] because it is a different thing with a
 * different lifetime: that one holds every passage in the book for as long as
 * the book is open, and this holds at most one word for as long as a panel is.
 * Only one, deliberately - a second word asked about while the first is still
 * arriving replaces it, because the panel the answer goes into is the same panel
 * and the reader has already moved on.
 *
 * The dictionary is asked whatever the word lookup setting says, and that is the
 * point rather than an oversight: somebody translating with a model has a word
 * the model chose one sense of, and this is where the others are. It costs
 * nothing and needs nothing configured.
 */
internal class ReaderWordLookup(
    private val lookUpWord: LookUpWordUseCase,
    private val settings: TranslationSettingsRepository,
    private val scope: CoroutineScope,
) {

    private val _inquiry = MutableStateFlow<WordInquiry?>(null)
    val inquiry: StateFlow<WordInquiry?> = _inquiry.asStateFlow()

    /**
     * Which passage the open inquiry is about - for putting the book's own word
     * back, which is the one thing the panel does to the page behind it.
     */
    var anchor: TextAnchor? = null
        private set

    private var asking: Job? = null

    /**
     * [sourceLanguage] is what the document declared it is written in, for the
     * sites that take a language pair. Null is not a problem: it becomes `auto`,
     * which is what those sites do with it anyway.
     */
    fun open(
        anchor: TextAnchor,
        word: String,
        showingOriginal: Boolean,
        sourceLanguage: String?,
    ) {
        asking?.cancel()

        val current = settings.settings.value

        this.anchor = anchor
        _inquiry.value = WordInquiry(
            word = word,
            entry = UiState.Loading,
            showingOriginal = showingOriginal,
            site = current.linkFor(word, sourceLanguage),
        )

        asking = scope.launch {
            val answer = lookUpWord(word, current.targetLanguage).fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Failure(it.toAppError()) },
            )

            // Only if it is still the same question. A reader who held a second
            // word while the first was arriving is looking at the second one.
            _inquiry.value = _inquiry.value?.takeIf { it.word == word }?.copy(entry = answer)
        }
    }

    fun dismiss() {
        asking?.cancel()
        asking = null
        anchor = null
        _inquiry.value = null
    }
}

/**
 * The address of the reader's chosen site with this word written into it, or
 * null when there is nowhere to go.
 *
 * Null is an ordinary state rather than a failure: the reader may have chosen a
 * custom site and not yet said where it is, and a button that opens a browser on
 * a search page with no search in it is worse than no button.
 */
private fun TranslationSettings.linkFor(word: String, sourceLanguage: String?): WordSiteLink? {
    val template = wordSiteUrlTemplate ?: return null

    val url = wordSite.urlFor(
        template = template,
        word = word,
        targetLanguage = targetLanguage,
        sourceLanguage = sourceLanguage,
    ) ?: return null

    return WordSiteLink(label = wordSite.label, url = url)
}
