package io.lukia.features.reader.presentation.components.translation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.displayShortMessage
import io.lukia.features.reader.presentation.translation.WordInquiry
import io.lukia.features.translate.domain.lookup.WordEntry
import io.lukia.shared.presentation.components.controls.ButtonSize
import io.lukia.shared.presentation.components.controls.ButtonVariant
import io.lukia.shared.presentation.components.controls.LukiaButton
import io.lukia.shared.presentation.components.surface.OverlayPanel
import io.lukia.shared.presentation.components.surface.OverlayPanelTopClearance
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space

/**
 * Everything else the reader can ask about a word they have already looked up.
 *
 * Opened by holding that word, which is the gesture that used to put the book's
 * own word back and now offers that as one of three things - so nothing the hold
 * could do before has gone, and two things it could not do are here.
 *
 * A panel rather than a menu anchored at the finger, and for the same reason the
 * reader's other panels are panels: what is in it is a dictionary entry of
 * several lines, which is not a menu row, and a panel is the one shape in this
 * app that can be as tall as its contents on a phone and on a desktop alike.
 *
 * The dictionary is asked whatever the word lookup setting is, because it costs
 * nothing and because somebody reading with a model has a word the model chose
 * one sense of - see
 * [io.lukia.features.translate.infrastructure.wiktionary.WiktionaryDictionary].
 */
@Composable
internal fun WordLookupPanel(
    inquiry: WordInquiry?,
    onDismiss: () -> Unit,
    onFlip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalLukiaDimens.current

    OverlayPanel(
        visible = inquiry != null,
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        // Held after it goes, so the panel fades out with the word still in it
        // rather than emptying first and then fading.
        val shown = inquiry ?: return@OverlayPanel

        Column(
            verticalArrangement = Arrangement.spacedBy(Space.lg),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = OverlayPanelTopClearance,
                    bottom = dimens.sectionSpacing,
                    start = dimens.screenPadding,
                    end = dimens.screenPadding,
                ),
        ) {
            Text(
                text = shown.word,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            WordSenses(state = shown.entry)

            WordLookupActions(inquiry = shown, onDismiss = onDismiss, onFlip = onFlip)
        }
    }
}

/**
 * Every sense the dictionary has, numbered.
 *
 * Numbered because that is what makes it read as an entry rather than as a
 * paragraph: the whole reason somebody opened this is that a word had more than
 * one meaning, and a run of sentences does not say where one ends.
 */
@Composable
private fun WordSenses(state: UiState<WordEntry>) {
    when (state) {
        is UiState.Loading -> Text(
            text = "Looking it up…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Inline rather than the app's error state, which fills what it is given:
        // nothing here has failed to open, and the site below is still worth
        // offering. A dictionary with no entry for a word is an ordinary answer.
        is UiState.Failure -> Text(
            text = state.error.displayShortMessage(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        is UiState.Success -> Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            state.data.senses.forEachIndexed { index, sense ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(end = Space.sm),
                    )

                    Text(
                        text = sense,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/**
 * What the reader can do from here: go back to the book's own word, or leave for
 * somebody else's page about it.
 *
 * The site opens in whatever the device uses for a link, which is the honest
 * answer for a page this app did not write and cannot lay out. It is missing
 * entirely when the reader has chosen a custom site and not yet said where it is
 * - a button that opens a browser on nothing is worse than no button.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordLookupActions(
    inquiry: WordInquiry,
    onDismiss: () -> Unit,
    onFlip: () -> Unit,
) {
    val links = LocalUriHandler.current

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
        modifier = Modifier.fillMaxWidth().padding(top = TopGutter),
    ) {
        LukiaButton(
            label = if (inquiry.showingOriginal) "Show the translation" else "Show the original",
            onClick = onFlip,
            variant = ButtonVariant.Secondary,
            size = ButtonSize.Compact,
        )

        inquiry.site?.let { site ->
            LukiaButton(
                label = "Open in ${site.label}",
                onClick = {
                    links.openUri(site.url)
                    onDismiss()
                },
                variant = ButtonVariant.Secondary,
                size = ButtonSize.Compact,
            )
        }
    }
}

/** A step down from the entry, so the buttons read as what to do about it. */
private val TopGutter = 4.dp
