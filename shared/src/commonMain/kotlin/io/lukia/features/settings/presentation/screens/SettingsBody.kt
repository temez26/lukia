package io.lukia.features.settings.presentation.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.settings.domain.AppSettings
import io.lukia.features.settings.domain.AppTheme
import io.lukia.features.settings.presentation.AvailableModels
import io.lukia.features.settings.presentation.ConnectionTest
import io.lukia.features.settings.presentation.ModelReadiness
import io.lukia.features.settings.presentation.components.ChoiceList
import io.lukia.features.settings.presentation.components.SettingsTextField
import io.lukia.features.translate.domain.PassageUnit
import io.lukia.features.translate.domain.TargetLanguage
import io.lukia.features.translate.domain.TranslationProvider
import io.lukia.features.translate.domain.TranslationSettings
import io.lukia.features.translate.domain.WordLookup
import io.lukia.shared.presentation.components.SegmentedControl
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space

/** Vertical room reserved so content starts clear of the floating back control. */
private val TopChromeClearance: Dp = 68.dp

/** Room past the last group, so the settings do not end hard against the edge. */
private val SettingsBottomClearance: Dp = 32.dp

/**
 * The least room a group is given, however little it has to put in it.
 *
 * Appearance is one control and Translating is a dozen, and a page that shrank
 * to fit whichever was open made switching between them feel like opening and
 * closing something rather than moving along a row. Held at about the height of
 * a phone's content area, the shortest group still fills the view and the
 * longest is the only one that scrolls.
 */
private val MinAreaHeight: Dp = 400.dp

/**
 * How long one group takes to give way to the next, and how long the page takes
 * to reach the new height.
 *
 * The words go first and the height follows, which is the order that reads as
 * one movement rather than as two things happening to the same screen.
 */
private const val AreaFadeMillis = 140
private const val AreaSettleMillis = 220

/**
 * The settings themselves, without the chrome around them.
 *
 * Shared, because these are shown two ways - as a screen of its own on a phone,
 * and as a panel over the library on anything wider - and a setting must not
 * appear or read differently depending on which one you reached.
 *
 * It keeps its title where the library dropped one. The library is the app's home
 * and needs no introduction; arriving here from a menu does.
 */
@Composable
internal fun SettingsBody(
    settings: AppSettings,
    translation: TranslationSettings,
    connectionTest: ConnectionTest,
    availableModels: AvailableModels,
    modelReadiness: ModelReadiness,
    onThemeChange: (AppTheme) -> Unit,
    onTargetLanguageChange: (TargetLanguage) -> Unit,
    onProviderChange: (TranslationProvider) -> Unit,
    onModelChange: (String) -> Unit,
    onModelChoose: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onTimeoutChange: (Int) -> Unit,
    onWordLookupChange: (WordLookup) -> Unit,
    onGoogleApiKeyChange: (String) -> Unit,
    onTapGestureChange: (PassageUnit?) -> Unit,
    onLongPressGestureChange: (PassageUnit?) -> Unit,
    onTranslateOnScreenChange: (Boolean) -> Unit,
    onTranslateAsYouReadChange: (Boolean) -> Unit,
    onTestConnection: () -> Unit,
    onLoadModels: () -> Unit,
    onLoadChosenModel: () -> Unit,
    modifier: Modifier = Modifier,
    topClearance: Dp = TopChromeClearance,
    bottomClearance: Dp = SettingsBottomClearance,
    /**
     * What a group sits in. It has to contrast with whatever is behind this body,
     * and that differs: the screen lays it on the window background, the panel on
     * a raised surface, and in the dark theme the background is true black.
     */
    groupColor: Color = MaterialTheme.colorScheme.surface,
) {
    val dimens = LocalLukiaDimens.current
    val scroll = rememberScrollState()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.verticalScroll(scroll),
    ) {
        Spacer(modifier = Modifier.height(topClearance))

        Column(
            modifier = Modifier
                .widthIn(max = dimens.maxDetailWidth)
                .fillMaxWidth()
                .padding(horizontal = dimens.screenPadding),
        ) {
            // Open on the first one rather than on a menu of them. Settings is
            // three groups, not a place to navigate around: the same control the
            // library narrows itself with switches between them, in place, with
            // nothing to go back out of.
            var area by rememberSaveable { mutableStateOf(SettingsArea.Default) }

            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            SegmentedControl(
                options = SettingsArea.entries,
                selected = area,
                label = { it.title },
                onSelect = { area = it },
                modifier = Modifier.padding(top = Space.lg),
            )

            // Back to the top of whatever was opened. Switching from a long
            // group to a short one while scrolled down otherwise lands the
            // reader at the bottom of something they have not seen the top of.
            LaunchedEffect(area) { scroll.animateScrollTo(0) }

            // One group fades into the next while the page eases between their
            // heights. Switched without either, the page jumps from the height
            // of one group to the height of another - which reads as the screen
            // being rebuilt rather than as a control being used, and is worse
            // the further apart the two groups are in length.
            Crossfade(
                targetState = area,
                animationSpec = tween(AreaFadeMillis, easing = LinearOutSlowInEasing),
                modifier = Modifier
                    .animateContentSize(
                        animationSpec = tween(AreaSettleMillis, easing = FastOutSlowInEasing),
                    )
                    // Applied inside the animation, so what eases between the
                    // groups is already the held height rather than the height
                    // of whichever group happens to be shortest.
                    .heightIn(min = MinAreaHeight),
            ) { shown ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    when (shown) {
                        SettingsArea.Appearance -> {
                            SettingsSection(
                                title = "Appearance",
                                // Named for what it does rather than what it is called, since
                                // "System" is the only option whose meaning is not obvious from
                                // the word alone.
                                footnote = "System follows your device’s light and dark setting.",
                                groupColor = groupColor,
                                modifier = Modifier.padding(top = dimens.sectionSpacing),
                            ) {
                                SegmentedControl(
                                    options = AppTheme.entries,
                                    selected = settings.theme,
                                    label = { it.name },
                                    onSelect = onThemeChange,
                                )
                            }
                        }

                        SettingsArea.Reading -> {
                            SettingsSection(
                                title = "Pointing at the book",
                                footnote = "Each does one thing, always. Doing it again on the same " +
                                    "passage puts the book\u2019s own words back.",
                                groupColor = groupColor,
                                modifier = Modifier.padding(top = dimens.sectionSpacing),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                                    LabelledField(label = "Tap") {
                                        GestureControl(
                                            selected = translation.gestures.tap,
                                            onSelect = onTapGestureChange,
                                        )
                                    }

                                    LabelledField(label = "Press and hold") {
                                        GestureControl(
                                            selected = translation.gestures.longPress,
                                            onSelect = onLongPressGestureChange,
                                        )
                                    }
                                }
                            }

                            SettingsSection(
                                title = "Translating ahead",
                                footnote = "Both are new, and both are slow and expensive: they " +
                                    "translate a screenful at a time rather than the passage you " +
                                    "pointed at. Off until you ask for them, and hidden from the " +
                                    "reader entirely while they are.",
                                groupColor = groupColor,
                                modifier = Modifier.padding(top = dimens.sectionSpacing),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                                    BetaToggle(
                                        label = "Translate what is on screen",
                                        detail = "Adds a button that translates every sentence you " +
                                            "can see, once.",
                                        checked = translation.bulkTranslation.onScreen,
                                        onChange = onTranslateOnScreenChange,
                                    )

                                    BetaToggle(
                                        label = "Keep translating as you read",
                                        detail = "Adds a button that goes on translating whatever " +
                                            "scrolls into view until you turn it off. This is the " +
                                            "expensive one.",
                                        checked = translation.bulkTranslation.asYouRead,
                                        onChange = onTranslateAsYouReadChange,
                                    )
                                }
                            }
                        }

                        SettingsArea.Translating -> {
                            SettingsSection(
                                title = "Translation service",
                                footnote = translation.provider.footnote(),
                                groupColor = groupColor,
                                modifier = Modifier.padding(top = dimens.sectionSpacing),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                                    SegmentedControl(
                                        options = TranslationProvider.entries,
                                        selected = translation.provider,
                                        label = { it.label },
                                        onSelect = onProviderChange,
                                    )

                                    if (translation.provider.hasConfigurableEndpoint) {
                                        LabelledField(label = "Address") {
                                            SettingsTextField(
                                                value = translation.baseUrl,
                                                onValueChange = onBaseUrlChange,
                                                placeholder = translation.provider.defaultBaseUrl,
                                            )
                                        }
                                    }

                                    val apiKeyLabel =
                                        if (translation.provider.requiresKey) "API key" else "API key (optional)"
                                    LabelledField(label = apiKeyLabel) {
                                        SettingsTextField(
                                            value = translation.apiKey.orEmpty(),
                                            onValueChange = onApiKeyChange,
                                            placeholder = if (translation.provider.requiresKey) "Required" else "None",
                                            secret = true,
                                        )
                                    }

                                    LabelledField(label = "Model") {
                                        SettingsTextField(
                                            value = translation.model,
                                            onValueChange = onModelChange,
                                            placeholder = translation.provider.defaultModel,
                                        )
                                    }

                                    if (translation.provider.hasConfigurableEndpoint) {
                                        ServerModels(
                                            state = availableModels,
                                            selected = translation.model,
                                            onSelect = onModelChoose,
                                            onLoad = onLoadModels,
                                        )

                                        ModelReadinessRow(
                                            state = modelReadiness,
                                            model = translation.model,
                                            onLoad = onLoadChosenModel,
                                        )
                                    } else if (translation.provider.suggestedModels.isNotEmpty()) {
                                        ChoiceList(
                                            options = translation.provider.suggestedModels,
                                            selected = translation.model,
                                            label = { it },
                                            onSelect = onModelChoose,
                                        )
                                    }

                                    TimeoutField(
                                        provider = translation.provider,
                                        seconds = translation.timeoutSeconds,
                                        onChange = onTimeoutChange,
                                    )

                                    ConnectionTestRow(
                                        state = connectionTest,
                                        enabled = translation.isConfigured,
                                        onTest = onTestConnection,
                                    )
                                }
                            }

                            SettingsSection(
                                title = "Looking up a word",
                                footnote = translation.wordLookup.footnote,
                                groupColor = groupColor,
                                modifier = Modifier.padding(top = dimens.sectionSpacing),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                                    SegmentedControl(
                                        options = WordLookup.entries,
                                        selected = translation.wordLookup,
                                        label = { it.label },
                                        onSelect = onWordLookupChange,
                                    )

                                    // Only where it does anything. Google is the one choice here
                                    // with something to configure, and a key field under a
                                    // dictionary that has never heard of one is a field that
                                    // asks a question with no answer.
                                    if (translation.wordLookup == WordLookup.Google) {
                                        LabelledField(label = "Cloud Translation key (optional)") {
                                            SettingsTextField(
                                                value = translation.googleApiKey.orEmpty(),
                                                onValueChange = onGoogleApiKeyChange,
                                                placeholder = "Public endpoint",
                                                secret = true,
                                            )
                                        }
                                    }
                                }
                            }

                            SettingsSection(
                                title = "Translate into",
                                footnote = "Tap a sentence while reading to translate it, and " +
                                    "tap it again for the original.",
                                groupColor = groupColor,
                                padding = Space.xs,
                                modifier = Modifier.padding(top = dimens.sectionSpacing),
                            ) {
                                ChoiceList(
                                    options = TargetLanguage.entries,
                                    selected = translation.targetLanguage,
                                    label = { it.endonym },
                                    // The English name too, so somebody who cannot yet read the
                                    // language they are learning can still find it in the list.
                                    detail = { it.englishName.takeIf { name -> name != it.endonym } },
                                    onSelect = onTargetLanguageChange,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(bottomClearance))
    }
}
