package io.lukia.features.reader.presentation.screens.translation

import androidx.compose.runtime.Immutable
import io.lukia.features.reader.domain.text.TextAnchor
import io.lukia.features.reader.presentation.translation.PassageSelection
import io.lukia.features.reader.presentation.translation.PassageTranslation
import io.lukia.features.reader.presentation.translation.ShownTranslations
import io.lukia.features.reader.presentation.translation.TranslateOnScreen
import io.lukia.features.reader.presentation.translation.WordInquiry
import io.lukia.features.translate.domain.BulkTranslation
import io.lukia.features.translate.domain.settings.TargetLanguage
import io.lukia.features.translate.domain.settings.TranslationGestures

/**
 * Everything the reader screen is told about translating, as one value.
 *
 * The defaults are a reader who has translated nothing and set nothing up.
 */
@Immutable
internal data class ReaderTranslating(
    /** What has become of each passage somebody has asked about, by where it is. */
    val passages: Map<TextAnchor, PassageTranslation> = emptyMap(),
    /** Whether what the reader can see should be translated without being tapped. */
    val onScreen: TranslateOnScreen = TranslateOnScreen.Idle,
    /** Whether a translation service is set up, which the controls needing a model read. */
    val serviceConfigured: Boolean = false,
    val shown: ShownTranslations = ShownTranslations.None,
    /** What a tap and a long press on the book ask for. */
    val gestures: TranslationGestures = TranslationGestures.Default,
    val targetLanguage: TargetLanguage = TargetLanguage.Default,
    /** Which of the two ways of translating more than one passage are offered at all. */
    val offered: BulkTranslation = BulkTranslation.Default,
    /** The one word the reader has held to ask about properly, if any. */
    val wordInquiry: WordInquiry? = null,
    /** Whether the translator's asides are set under the paragraphs they belong to. */
    val notes: Boolean = true,
)

/**
 * Everything the reader screen can ask of translating.
 *
 * Apart from [ReaderTranslating] so that it can be built once and remembered:
 * the state changes with every passage, and the callbacks never do.
 */
internal class ReaderTranslatingActions(
    val onPassageSelected: (PassageSelection) -> Unit = {},
    /** A hold on a word already answered, which opens what else it means. */
    val onPassageHold: (PassageSelection) -> Unit = {},
    /** The second mouse button on a word, which opens what it means either way. */
    val onPassageInquiry: (PassageSelection) -> Unit = {},
    val onPassagesRequested: (List<PassageSelection>) -> Unit = {},
    val onWordInquiryDismiss: () -> Unit = {},
    val onInquiredWordAction: () -> Unit = {},
    val onTranslateOnScreen: () -> Unit = {},
    val onContinuousChange: (Boolean) -> Unit = {},
    val onVisibleChange: (Boolean) -> Unit = {},
    val onTargetLanguageChange: (TargetLanguage) -> Unit = {},
    val onGesturesChange: (TranslationGestures) -> Unit = {},
    val onNotesChange: (Boolean) -> Unit = {},
)
