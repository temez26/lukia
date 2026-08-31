package io.lukia.features.translate.presentation

import io.lukia.features.translate.application.TranslatePassageUseCase
import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.domain.TranslationRequest
import io.lukia.features.translate.domain.Translator
import io.lukia.features.translate.domain.settings.TargetLanguage
import io.lukia.features.translate.domain.text.TextAnchor
import kotlinx.coroutines.test.TestScope

/**
 * A session under test, and the taps and requests it is given.
 */

internal val anchor = TextAnchor.Section(sectionIndex = 0, blockIndex = 1, sentenceIndex = 2)

internal fun TestScope.session(
    translate: suspend (TranslationRequest) -> Translation,
) = TranslationSession(
    TranslatePassageUseCase(Translator { request, _ -> translate(request) }),
    this,
)

internal fun anchor(sentenceIndex: Int) =
    TextAnchor.Section(sectionIndex = 0, blockIndex = 0, sentenceIndex = sentenceIndex)

internal fun TranslationSession.tap(
    anchor: TextAnchor,
    passage: String = "Call me Ishmael.",
) = onPassageTapped(anchor) { request(passage) }

internal fun TranslationSession.ready(anchor: TextAnchor) =
    passages.value[anchor] as PassageTranslation.Ready

internal fun request(passage: String) = TranslationRequest(
    text = passage,
    targetLanguage = TargetLanguage.Finnish,
)
