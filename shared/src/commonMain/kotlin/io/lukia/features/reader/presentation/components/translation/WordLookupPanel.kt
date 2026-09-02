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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.displayShortMessage
import io.lukia.features.reader.presentation.translation.WordInquiry
import io.lukia.features.reader.presentation.translation.WordOnThePage
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
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalLukiaDimens.current

    // Held through the closing animation so the panel goes out with the word
    // still in it - see BookDetailOverlay, which closes the same way.
    //
    // Without this the panel empties the instant the word goes and the fade runs
    // on nothing: the surface loses every line it was sized by, drops to the
    // height of its own padding, and only then begins to leave. What that reads
    // as is the box collapsing rather than closing.
    var lastShown by remember { mutableStateOf<WordInquiry?>(null) }

    if (inquiry != null) lastShown = inquiry

    val shown = inquiry ?: lastShown ?: return

    OverlayPanel(
        visible = inquiry != null,
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
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
            WordHeading(inquiry = shown)

            WordSenses(state = shown.entry)

            WordLookupActions(inquiry = shown, onDismiss = onDismiss, onAction = onAction)
        }
    }
}

/**
 * The word, and the form the dictionary answered under when that is a different
 * one.
 *
 * A book is written in inflected words, and a wiki's page for one of them often
 * only says which word it is a form of - so the entry that comes back is the
 * base form's. Saying so is worth a line: a reader who held `values` and is
 * being shown the senses of `value` should be able to see that, both to trust
 * the answer and because knowing the base form is half of learning the word.
 */
@Composable
private fun WordHeading(inquiry: WordInquiry) {
    val defined = (inquiry.entry as? UiState.Success)?.data?.word

    Column {
        Text(
            text = inquiry.word,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        if (defined != null && !defined.equals(inquiry.word, ignoreCase = true)) {
            Text(
                text = "Looked up under “$defined”",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    onAction: () -> Unit,
) {
    val links = LocalUriHandler.current

    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        // Why the reader is looking at this panel rather than at a translation,
        // said before the button that offers to try again - a reason to press it
        // or to reach for the site instead.
        inquiry.unansweredFor()?.let { reason ->
            Text(
                text = reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
            modifier = Modifier.fillMaxWidth().padding(top = TopGutter),
        ) {
            LukiaButton(
                label = inquiry.onThePage.label(),
                onClick = onAction,
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
}

/**
 * Why the word on the page has no translation, or null when saying so would be
 * saying it twice.
 *
 * A reader whose word lookup *is* the dictionary gets one failure, not two: the
 * service that could not answer the word in the paragraph is the same service
 * this panel just asked, and it comes back with the same sentence. Printed in
 * both places it reads as two things having gone wrong.
 *
 * Anything else is worth saying. A model that refused the word while the
 * dictionary answered it is the case this panel exists for, and the reason is
 * what makes sense of the button offering to ask again.
 */
private fun WordInquiry.unansweredFor(): String? {
    val failed = (onThePage as? WordOnThePage.Failed)?.message ?: return null

    return failed.takeIf { it != (entry as? UiState.Failure)?.error?.displayShortMessage() }
}

/** What the one button that touches the page behind the panel offers to do. */
private fun WordOnThePage.label(): String = when (this) {
    WordOnThePage.Translated -> "Show the original"
    WordOnThePage.Original -> "Show the translation"
    is WordOnThePage.Failed -> "Try again"
}

/** A step down from the entry, so the buttons read as what to do about it. */
private val TopGutter = 4.dp
