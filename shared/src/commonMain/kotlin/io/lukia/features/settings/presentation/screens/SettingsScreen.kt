package io.lukia.features.settings.presentation.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.settings.domain.AppSettings
import io.lukia.features.settings.domain.AppTheme
import io.lukia.features.settings.presentation.SettingsViewModel
import io.lukia.features.settings.presentation.AvailableModels
import io.lukia.features.settings.presentation.ModelReadiness
import io.lukia.features.settings.presentation.ConnectionTest
import io.lukia.features.settings.presentation.components.ChoiceList
import io.lukia.features.settings.presentation.components.SettingsTextField
import io.lukia.features.translate.domain.PassageUnit
import io.lukia.features.translate.domain.TargetLanguage
import io.lukia.features.translate.domain.TranslationProvider
import io.lukia.features.translate.domain.TranslationSettings
import io.lukia.features.translate.domain.WordLookup
import io.lukia.shared.presentation.components.ButtonVariant
import io.lukia.shared.presentation.components.LukiaButton
import io.lukia.shared.presentation.components.BackChevron
import io.lukia.shared.presentation.components.GroupCard
import io.lukia.shared.presentation.components.IconButtonVariant
import io.lukia.shared.presentation.components.LukiaIconButton
import io.lukia.core.presentation.displayShortMessage
import io.lukia.shared.presentation.components.SectionLabel
import io.lukia.shared.presentation.components.SegmentedControl
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import org.koin.compose.viewmodel.koinViewModel

/** Vertical room reserved so content starts clear of the floating back control. */
private val TopChromeClearance: Dp = 68.dp

/** Room past the last group, so the settings do not end hard against the edge. */
private val SettingsBottomClearance: Dp = 32.dp

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val translation by viewModel.translationSettings.collectAsState()
    val connectionTest by viewModel.connectionTest.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val modelReadiness by viewModel.modelReadiness.collectAsState()

    SettingsContent(
        settings = settings,
        translation = translation,
        connectionTest = connectionTest,
        availableModels = availableModels,
        modelReadiness = modelReadiness,
        onThemeChange = viewModel::setTheme,
        onTargetLanguageChange = viewModel::setTargetLanguage,
        onProviderChange = viewModel::setProvider,
        onModelChange = viewModel::setModel,
        onModelChoose = viewModel::chooseModel,
        onApiKeyChange = viewModel::setApiKey,
        onBaseUrlChange = viewModel::onBaseUrlEdited,
        onTimeoutChange = viewModel::setTimeoutSeconds,
        onWordLookupChange = viewModel::setWordLookup,
        onGoogleApiKeyChange = viewModel::setGoogleApiKey,
        onTapGestureChange = viewModel::setTapGesture,
        onLongPressGestureChange = viewModel::setLongPressGesture,
        onTranslateOnScreenChange = viewModel::setTranslateOnScreen,
        onTranslateAsYouReadChange = viewModel::setTranslateAsYouRead,
        onTestConnection = viewModel::testConnection,
        onLoadModels = viewModel::loadAvailableModels,
        onLoadChosenModel = viewModel::loadChosenModel,
        onBack = onBack
    )
}

/**
 * Stateless half of the screen, matching the other screens so the layout can be
 * rendered without a ViewModel or dependency graph.
 *
 * This screen keeps a title where the library dropped one. A pushed screen has to
 * say what it is - the library is the app's home and needs no introduction, but
 * arriving here from a menu does.
 */
@Composable
fun SettingsContent(
    settings: AppSettings,
    translation: TranslationSettings,
    connectionTest: ConnectionTest = ConnectionTest.Untried,
    availableModels: AvailableModels = AvailableModels.Unknown,
    modelReadiness: ModelReadiness = ModelReadiness.Cold,
    onThemeChange: (AppTheme) -> Unit,
    onTargetLanguageChange: (TargetLanguage) -> Unit,
    onProviderChange: (TranslationProvider) -> Unit,
    onModelChange: (String) -> Unit,
    onModelChoose: (String) -> Unit = onModelChange,
    onApiKeyChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onTimeoutChange: (Int) -> Unit = {},
    onWordLookupChange: (WordLookup) -> Unit = {},
    onGoogleApiKeyChange: (String) -> Unit = {},
    onTapGestureChange: (PassageUnit?) -> Unit = {},
    onLongPressGestureChange: (PassageUnit?) -> Unit = {},
    onTranslateOnScreenChange: (Boolean) -> Unit = {},
    onTranslateAsYouReadChange: (Boolean) -> Unit = {},
    onTestConnection: () -> Unit,
    onLoadModels: () -> Unit = {},
    onLoadChosenModel: () -> Unit = {},
    onBack: () -> Unit
) {
    val dimens = LocalLukiaDimens.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Full-bleed like the detail screen, so content passes under the floating
        // back button - which means window insets are applied by hand. The
        // background above stays outside the inset so colour still reaches the
        // screen edges. Resolves to zero on desktop.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            SettingsBody(
                settings = settings,
                translation = translation,
                connectionTest = connectionTest,
                availableModels = availableModels,
                modelReadiness = modelReadiness,
                onThemeChange = onThemeChange,
                onTargetLanguageChange = onTargetLanguageChange,
                onProviderChange = onProviderChange,
                onModelChange = onModelChange,
                onModelChoose = onModelChoose,
                onApiKeyChange = onApiKeyChange,
                onBaseUrlChange = onBaseUrlChange,
                onTimeoutChange = onTimeoutChange,
                onWordLookupChange = onWordLookupChange,
                onGoogleApiKeyChange = onGoogleApiKeyChange,
                onTapGestureChange = onTapGestureChange,
                onLongPressGestureChange = onLongPressGestureChange,
                onTranslateOnScreenChange = onTranslateOnScreenChange,
                onTranslateAsYouReadChange = onTranslateAsYouReadChange,
                onTestConnection = onTestConnection,
                onLoadModels = onLoadModels,
                onLoadChosenModel = onLoadChosenModel,
                modifier = Modifier.fillMaxSize()
            )

            LukiaIconButton(
                label = "Go back",
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = dimens.screenPadding, top = Space.lg),
                variant = IconButtonVariant.Glass
            ) { tint, glyph ->
                BackChevron(tint = tint, modifier = glyph)
            }
        }
    }
}

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
    groupColor: Color = MaterialTheme.colorScheme.surface
) {
    val dimens = LocalLukiaDimens.current
    val scroll = rememberScrollState()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.verticalScroll(scroll)
    ) {
        Spacer(modifier = Modifier.height(topClearance))

        Column(
            modifier = Modifier
                .widthIn(max = dimens.maxDetailWidth)
                .fillMaxWidth()
                .padding(horizontal = dimens.screenPadding)
        ) {

            // Open on the first one rather than on a menu of them. Settings is
            // three groups, not a place to navigate around: the same control the
            // library narrows itself with switches between them, in place, with
            // nothing to go back out of.
            var area by rememberSaveable { mutableStateOf(SettingsArea.Default) }

            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            SegmentedControl(
                options = SettingsArea.entries,
                selected = area,
                label = { it.title },
                onSelect = { area = it },
                modifier = Modifier.padding(top = Space.lg)
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
                        animationSpec = tween(AreaSettleMillis, easing = FastOutSlowInEasing)
                    )
                    // Applied inside the animation, so what eases between the
                    // groups is already the held height rather than the height
                    // of whichever group happens to be shortest.
                    .heightIn(min = MinAreaHeight)
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
                                modifier = Modifier.padding(top = dimens.sectionSpacing)
                            ) {
                                SegmentedControl(
                                    options = AppTheme.entries,
                                    selected = settings.theme,
                                    label = { it.name },
                                    onSelect = onThemeChange
                                )
                            }
                        }

                        SettingsArea.Reading -> {
                            SettingsSection(
                                title = "Pointing at the book",
                                footnote = "Each does one thing, always. Doing it again on the same " +
                                    "passage puts the book\u2019s own words back.",
                                groupColor = groupColor,
                                modifier = Modifier.padding(top = dimens.sectionSpacing)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                                    LabelledField(label = "Tap") {
                                        GestureControl(
                                            selected = translation.gestures.tap,
                                            onSelect = onTapGestureChange
                                        )
                                    }

                                    LabelledField(label = "Press and hold") {
                                        GestureControl(
                                            selected = translation.gestures.longPress,
                                            onSelect = onLongPressGestureChange
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
                                modifier = Modifier.padding(top = dimens.sectionSpacing)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                                    BetaToggle(
                                        label = "Translate what is on screen",
                                        detail = "Adds a button that translates every sentence you " +
                                            "can see, once.",
                                        checked = translation.bulkTranslation.onScreen,
                                        onChange = onTranslateOnScreenChange
                                    )

                                    BetaToggle(
                                        label = "Keep translating as you read",
                                        detail = "Adds a button that goes on translating whatever " +
                                            "scrolls into view until you turn it off. This is the " +
                                            "expensive one.",
                                        checked = translation.bulkTranslation.asYouRead,
                                        onChange = onTranslateAsYouReadChange
                                    )
                                }
                            }
                        }

                        SettingsArea.Translating -> {
                            SettingsSection(
                                title = "Translation service",
                                footnote = translation.provider.footnote(),
                                groupColor = groupColor,
                                modifier = Modifier.padding(top = dimens.sectionSpacing)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                                    SegmentedControl(
                                        options = TranslationProvider.entries,
                                        selected = translation.provider,
                                        label = { it.label },
                                        onSelect = onProviderChange
                                    )

                                    if (translation.provider.hasConfigurableEndpoint) {
                                        LabelledField(label = "Address") {
                                            SettingsTextField(
                                                value = translation.baseUrl,
                                                onValueChange = onBaseUrlChange,
                                                placeholder = translation.provider.defaultBaseUrl
                                            )
                                        }
                                    }

                                    LabelledField(
                                        label = if (translation.provider.requiresKey) "API key" else "API key (optional)"
                                    ) {
                                        SettingsTextField(
                                            value = translation.apiKey.orEmpty(),
                                            onValueChange = onApiKeyChange,
                                            placeholder = if (translation.provider.requiresKey) "Required" else "None",
                                            secret = true
                                        )
                                    }

                                    LabelledField(label = "Model") {
                                        SettingsTextField(
                                            value = translation.model,
                                            onValueChange = onModelChange,
                                            placeholder = translation.provider.defaultModel
                                        )
                                    }

                                    if (translation.provider.hasConfigurableEndpoint) {
                                        ServerModels(
                                            state = availableModels,
                                            selected = translation.model,
                                            onSelect = onModelChoose,
                                            onLoad = onLoadModels
                                        )

                                        ModelReadinessRow(
                                            state = modelReadiness,
                                            model = translation.model,
                                            onLoad = onLoadChosenModel
                                        )
                                    } else if (translation.provider.suggestedModels.isNotEmpty()) {
                                        ChoiceList(
                                            options = translation.provider.suggestedModels,
                                            selected = translation.model,
                                            label = { it },
                                            onSelect = onModelChoose
                                        )
                                    }

                                    TimeoutField(
                                        provider = translation.provider,
                                        seconds = translation.timeoutSeconds,
                                        onChange = onTimeoutChange
                                    )

                                    ConnectionTestRow(
                                        state = connectionTest,
                                        enabled = translation.isConfigured,
                                        onTest = onTestConnection
                                    )
                                }
                            }

                            SettingsSection(
                                title = "Looking up a word",
                                footnote = translation.wordLookup.footnote,
                                groupColor = groupColor,
                                modifier = Modifier.padding(top = dimens.sectionSpacing)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                                    SegmentedControl(
                                        options = WordLookup.entries,
                                        selected = translation.wordLookup,
                                        label = { it.label },
                                        onSelect = onWordLookupChange
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
                                                secret = true
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
                                modifier = Modifier.padding(top = dimens.sectionSpacing)
                            ) {
                                ChoiceList(
                                    options = TargetLanguage.entries,
                                    selected = translation.targetLanguage,
                                    label = { it.endonym },
                                    // The English name too, so somebody who cannot yet read the
                                    // language they are learning can still find it in the list.
                                    detail = { it.englishName.takeIf { name -> name != it.endonym } },
                                    onSelect = onTargetLanguageChange
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
 * The parts of the app somebody comes to Settings to change.
 *
 * Three rather than the eight sections behind them, because a settings page is
 * read by somebody who already knows what they want to change and is looking for
 * where it lives. Eight equally-weighted boxes in one column is a list to
 * search; three segments across the top is a question with an answer.
 */
enum class SettingsArea(val title: String) {

    Appearance("Appearance"),

    /** What a tap and a press do, and what the reader offers to translate. */
    Reading("Reading"),

    /** Who does the translating, what they are asked for, and into what. */
    Translating("Translating");

    companion object {

        /**
         * What Settings opens on. The one with the fewest ways to go wrong and
         * the one most people came for, which is also why it is first.
         */
        val Default = Appearance
    }
}

/**
 * A switch for something that is not finished, and says so.
 *
 * The label carries the word rather than the section, because these sit beside
 * each other and a reader turning one on is being told about that one. What the
 * beta actually means - slow, and it spends money a screenful at a time - is in
 * the detail underneath, where somebody deciding will read it.
 */
@Composable
private fun BetaToggle(
    label: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                BetaBadge(modifier = Modifier.padding(start = Space.sm))
            }

            Switch(checked = checked, onCheckedChange = onChange)
        }

        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BetaBadge(modifier: Modifier = Modifier) {
    Text(
        text = "BETA",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Space.xs, vertical = 1.dp)
    )
}

/**
 * What the reader's own server says it can run.
 *
 * Asked for rather than guessed at, because a local model's name has to match
 * exactly and is not something anybody recalls character for character. The
 * free-text field above this stays either way - a server that will not answer
 * the question can still translate perfectly well once its model is named by
 * hand.
 */
@Composable
private fun ServerModels(
    state: AvailableModels,
    selected: String,
    onSelect: (String) -> Unit,
    onLoad: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        LukiaButton(
            label = when (state) {
                AvailableModels.Loading -> "Asking the server…"
                is AvailableModels.Loaded -> "Reload models"
                else -> "Load models from server"
            },
            onClick = onLoad,
            variant = ButtonVariant.Secondary,
            enabled = state != AvailableModels.Loading
        )

        when (state) {
            AvailableModels.Unknown, AvailableModels.Loading -> Unit

            is AvailableModels.Loaded -> ChoiceList(
                options = state.models,
                selected = selected,
                label = { it },
                onSelect = onSelect
            )

            is AvailableModels.Failed -> Text(
                text = state.error.displayShortMessage(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * Whether the model the reader just picked is loaded and ready.
 *
 * The whole point of showing this is that the waiting has to happen somewhere.
 * A server holding a different model has to read this one off disk before it
 * can answer anything, and that is minutes for a large one - so it is spent
 * here, watched, rather than on the first sentence tapped in a book, where it
 * looks like the app has stopped.
 *
 * The button stays available once it has worked, because the reader can type a
 * model name by hand instead of picking one, and because a server restarted
 * since is a server that has forgotten.
 */
@Composable
private fun ModelReadinessRow(
    state: ModelReadiness,
    model: String,
    onLoad: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        LukiaButton(
            label = when (state) {
                ModelReadiness.Loading -> "Loading…"
                else -> "Load this model now"
            },
            onClick = onLoad,
            variant = ButtonVariant.Secondary,
            enabled = model.isNotBlank() && state != ModelReadiness.Loading
        )

        when (state) {
            ModelReadiness.Cold -> Unit

            ModelReadiness.Loading -> Text(
                text = "Asking the server to load $model. A large model can take a few " +
                    "minutes the first time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ModelReadiness.Ready -> Text(
                text = "$model is loaded and answering.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            is ModelReadiness.Failed -> Text(
                text = state.error.displayShortMessage(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * How long this provider may go quiet before the wait is given up on.
 *
 * Silence rather than total time, which is why the number can be this large
 * without a slow answer hanging the reader: a provider still sending is never
 * given up on, and one that has stopped is given up on after this.
 *
 * The typed text is kept here rather than pushed straight into settings so that
 * a field being cleared to retype it does not momentarily mean "no wait at
 * all". What leaves this composable is only ever a number.
 */
@Composable
private fun TimeoutField(
    provider: TranslationProvider,
    seconds: Int,
    onChange: (Int) -> Unit
) {
    // Keyed on the provider because each keeps its own, and switching between
    // them has to show the one now in force rather than what was last typed.
    var typed by remember(provider) { mutableStateOf(seconds.toString()) }

    LabelledField(label = "Give up after") {
        SettingsTextField(
            value = typed,
            onValueChange = { entry ->
                typed = entry.filter { it.isDigit() }.take(4)
                typed.toIntOrNull()?.let(onChange)
            },
            placeholder = "${provider.defaultTimeoutSeconds} seconds"
        )
    }
}

/**
 * What one gesture asks for, including nothing at all.
 *
 * Off is a choice in the same row as the units rather than a switch beside them,
 * because it is the same question: what does this gesture do? A reader who only
 * ever looks words up turns the tap off here and stops translating sentences by
 * accident.
 */
@Composable
private fun GestureControl(
    selected: PassageUnit?,
    onSelect: (PassageUnit?) -> Unit
) {
    SegmentedControl(
        options = GestureChoices,
        selected = selected,
        label = { it?.label ?: "Off" },
        onSelect = onSelect
    )
}

private val GestureChoices: List<PassageUnit?> = listOf(null) + PassageUnit.entries

/** A caption over one field, for a group holding several of them. */
@Composable
private fun LabelledField(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        content()
    }
}

/**
 * Proving the translator works, before a book is opened rather than during one.
 *
 * The sentence that comes back is shown rather than a tick, because "connected"
 * is not the question. A local server can answer perfectly and still return
 * prose instead of a translation, and a hosted one can accept the key and not
 * know the model - both of which look like success from any distance and are
 * obvious the moment the words are on screen.
 */
@Composable
private fun ConnectionTestRow(
    state: ConnectionTest,
    enabled: Boolean,
    onTest: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        LukiaButton(
            label = if (state is ConnectionTest.Trying) "Testing…" else "Test translation",
            onClick = onTest,
            variant = ButtonVariant.Secondary,
            enabled = enabled && state !is ConnectionTest.Trying
        )

        when (state) {
            ConnectionTest.Untried, ConnectionTest.Trying -> Unit

            is ConnectionTest.Worked -> Text(
                text = "\u201CThe sea was calm that morning.\u201D became \u201C${state.sample}\u201D",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            is ConnectionTest.Failed -> Text(
                text = state.error.displayShortMessage(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * What is worth saying under each provider's settings - which is different for
 * each, because what can go wrong with each is different.
 */
private fun TranslationProvider.footnote(): String = when (this) {
    TranslationProvider.Anthropic, TranslationProvider.OpenAi ->
        "Your key is stored on this device and sent only to ${label}, with the " +
            "sentence you tapped and what the book says about itself."

    TranslationProvider.Local ->
        "Nothing leaves your network. On a phone or tablet, \u201Clocalhost\u201D means the " +
            "device itself - use the computer\u2019s address on your network instead."
}

/**
 * One group of settings, in the style of an iOS grouped list: a header above a
 * rounded surface, with any explanation set below it rather than inside.
 */
@Composable
private fun SettingsSection(
    title: String,
    groupColor: Color,
    modifier: Modifier = Modifier,
    footnote: String? = null,
    /** Rows that draw their own vertical padding ask for none of the card's. */
    padding: Dp = Space.lg,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel(
            text = title,
            modifier = Modifier.padding(start = Space.xs, bottom = Space.sm)
        )

        GroupCard(color = groupColor) {
            Box(modifier = Modifier.padding(horizontal = Space.lg, vertical = padding)) {
                content()
            }
        }

        if (footnote != null) {
            Text(
                text = footnote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.xs, top = Space.sm)
            )
        }
    }
}
