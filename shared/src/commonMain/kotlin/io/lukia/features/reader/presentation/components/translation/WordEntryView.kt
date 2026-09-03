package io.lukia.features.reader.presentation.components.translation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.displayShortMessage
import io.lukia.features.reader.presentation.translation.WordInquiry
import io.lukia.features.translate.domain.lookup.WordEntry
import io.lukia.features.translate.domain.lookup.WordMeaning
import io.lukia.resources.Res
import io.lukia.resources.word_looked_up_under
import io.lukia.resources.word_looking_it_up
import io.lukia.resources.word_no_meaning
import io.lukia.resources.word_related
import io.lukia.resources.word_sense_number
import io.lukia.shared.presentation.theme.LukiaMotion
import io.lukia.shared.presentation.theme.Space
import io.lukia.shared.presentation.theme.softFadeOut
import io.lukia.shared.presentation.theme.stateSwapIn
import org.jetbrains.compose.resources.stringResource

/**
 * The word the reader held, and what the page says that word is.
 *
 * A book is written in inflected words, and a wiki's page for one of them often
 * only says which word it is a form of - so the entry that comes back is the
 * base form's. Saying so is worth a line: a reader who held `skills` and is
 * being shown the senses of `skill` should be able to see that, both to trust
 * the answer and because `monikkomuoto sanasta skill` is half of learning the
 * word. It is the half that cannot go over the word in the book, which is the
 * whole reason it is here.
 */
@Composable
internal fun WordHeading(inquiry: WordInquiry) {
    val entry = (inquiry.entry as? UiState.Success)?.data

    Column {
        Text(
            text = inquiry.word,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        entry?.formOf(inquiry.word)?.let { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * How the held word relates to the one that was defined, in the wiki's own words
 * where it had any and in the app's where it did not.
 *
 * Null when they are the same word, because "looked up under skill" under a
 * heading reading `skill` is a line that says nothing.
 */
@Composable
private fun WordEntry.formOf(held: String): String? = when {
    form != null -> form
    !word.equals(held, ignoreCase = true) ->
        stringResource(Res.string.word_looked_up_under, word)

    else -> null
}

/**
 * How much room the entry is given before there is one.
 *
 * The panel opens on a word and then waits on a wiki, so what it has to show at
 * the moment it arrives is a line saying so. Given only that line it opened as a
 * sliver and leapt to full height a moment later, which reads as two things
 * happening rather than one.
 *
 * About the three senses a short entry comes back with, so it opens at roughly
 * the size it will settle at. A longer entry still grows into place, and that
 * growth is animated rather than snapped.
 */
private val EntryMinHeight: Dp = 76.dp

/**
 * Everything the dictionary had about the word: what it is, what it means, and
 * what the entry lists beside it.
 *
 * The answer cross-dissolves in over the line that was waiting for it, on the
 * app's own swap - the same one every screen uses to trade a loading state for
 * its content - while the height it needs is eased into rather than jumped to.
 * Together those are what make the entry arrive in the panel rather than replace
 * it.
 */
@Composable
internal fun WordEntryBody(state: UiState<WordEntry>, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = state,
        modifier = modifier
            .heightIn(min = EntryMinHeight)
            .animateContentSize(animationSpec = LukiaMotion.standard()),
        transitionSpec = { (stateSwapIn() togetherWith softFadeOut()) using null },
        // Only the case matters: an entry whose senses changed is the same
        // answer, and nothing here changes one twice.
        contentKey = { current ->
            when (current) {
                is UiState.Loading -> "loading"
                is UiState.Failure -> "failure"
                is UiState.Success -> "success"
            }
        },
        label = "wordEntry",
    ) { current ->
        WordEntryContent(state = current)
    }
}

@Composable
private fun WordEntryContent(state: UiState<WordEntry>) {
    when (state) {
        is UiState.Loading -> Text(
            text = stringResource(Res.string.word_looking_it_up),
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

        is UiState.Success -> WordEntryDetails(entry = state.data)
    }
}

@Composable
private fun WordEntryDetails(entry: WordEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        if (entry.hasNoMeaning) {
            // Reached by a word whose page is nothing but grammar and whose base
            // form has no page at all. The form is already above this, so what is
            // left to say is that it was the whole answer rather than the start
            // of one.
            Text(
                text = stringResource(Res.string.word_no_meaning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        entry.meanings.forEach { meaning -> WordMeaningBlock(meaning = meaning) }

        if (entry.related.isNotEmpty()) WordsBeside(words = entry.related)
    }
}

/**
 * What the word means as one part of speech, under the name of that part of
 * speech.
 *
 * Kept apart rather than run together because a word is not one word. Somebody
 * who held `means` in a book about averages is looking for `keskiarvo`, and an
 * entry that opened with `ilkeä` and carried on into `tarkoittaa` without saying
 * which was which would read as one word with three unrelated meanings - which
 * is exactly what it is, and exactly what the headings are for.
 */
@Composable
private fun WordMeaningBlock(meaning: WordMeaning) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        meaning.partOfSpeech?.let { name ->
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        WordSenses(senses = meaning.senses)
    }
}

/**
 * Every sense the dictionary has of this part of speech, numbered.
 *
 * Numbered because that is what makes it read as an entry rather than as a
 * paragraph: the whole reason somebody opened this is that a word had more than
 * one meaning, and a run of sentences does not say where one ends.
 */
@Composable
private fun WordSenses(senses: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        senses.forEachIndexed { index, sense ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = stringResource(Res.string.word_sense_number, index + 1),
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

/**
 * The words the entry listed next to the senses - what the word is built from,
 * what is built on it, what is said with it.
 *
 * Run together on one line rather than listed, because they are not answers to
 * the question that was asked: somebody who held a word wanted to know what it
 * means, and these are what to look up next.
 */
@Composable
private fun WordsBeside(words: List<String>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Space.xxs),
        modifier = Modifier.padding(top = Space.xs),
    ) {
        Text(
            text = stringResource(Res.string.word_related),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = words.joinToString(SEPARATOR),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** What runs one short thing into the next without reading as punctuation. */
private const val SEPARATOR = "  ·  "
