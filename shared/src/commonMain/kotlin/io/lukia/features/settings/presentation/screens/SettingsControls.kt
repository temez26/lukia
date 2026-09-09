package io.lukia.features.settings.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.resources.Res
import io.lukia.resources.action_explain
import io.lukia.resources.settings_beta
import io.lukia.shared.presentation.components.controls.InfoButton
import io.lukia.shared.presentation.components.surface.GroupSection
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

// The parts a settings page is built from. None of them knows what a setting
// means - each takes a value, shows it, and reports a change.

/**
 * The parts of the app somebody comes to Settings to change.
 *
 * Five rather than the ten sections behind them: a settings page is read by
 * somebody who knows what they want to change and is looking for where it lives.
 * Ten equally-weighted boxes in a column is a list to search; five segments
 * across the top is a question with an answer.
 *
 * Ordered by how often they are opened, which is also how much machinery each
 * asks the reader to think about: what the app looks like, how books are set,
 * what is translated, who does it, and last which Lukia this is.
 */
enum class SettingsArea {

    Appearance,

    /** How a book is set: its page, its type, and what a PDF opens as. */
    Reading,

    /**
     * What is translated, when, into what, and who answers about a single word.
     *
     * Everything a reader has on the day they install the app. A word goes to a
     * dictionary or to Google over an ordinary web API, free and with nothing to
     * fill in, so all of it belongs beside the language rather than behind a
     * page about machinery.
     */
    Translating,

    /**
     * Everything translating does that only a model can do.
     *
     * The segment is named for the state all of it is in: Beta. It was called
     * Providers, which described an address, a key and a timeout, said nothing
     * about why a reader would open it, and put the dictionary that needs none
     * of them beside the service that is all of them. The enum keeps the longer
     * name because it says what the page holds; the segment says what a reader
     * is walking into.
     *
     * Apart from Translating because this is the group that can be filled in
     * wrongly and the one that costs money. Somebody changing the language they
     * read into should not scroll past an API key to reach it, nor be offered a
     * gesture asking for a paragraph nothing on their device can translate.
     */
    AiTranslation,

    /**
     * Which Lukia this is, what it was built from, and the record of what went
     * wrong while it ran.
     *
     * A segment rather than a group repeated under all the others. It sat at
     * the foot of every page so the version was always a scroll away, which
     * ended all four groups in a paragraph about licences that had nothing to do
     * with them, and lengthened every page for a thing looked at twice a year.
     * One press is not further away than the bottom of a scrolling page.
     */
    About,
    ;

    companion object {

        /**
         * What Settings opens on. The one with the fewest ways to go wrong and
         * the one most people came for, which is also why it is first.
         */
        val Default = Appearance
    }
}

/**
 * A switch for one setting, saying as little about it as the setting allows.
 *
 * Usually nothing. A setting here is named in a few words and left at that: a
 * row carrying a mark that opened a paragraph about itself made every group a
 * page to read before it could be used, and the group heading already says what
 * the group is for. [detail] is the exception - a line short enough to be read
 * whether or not anybody wanted it, for the settings that cost money or throw
 * something away.
 *
 * The switch is the only part of the row that is a control. The whole row used
 * to be, which reads well on a phone's settings list and badly here: a reader
 * reaching for the words about a setting flipped it instead.
 *
 * The label takes the width and wraps; the switch keeps its own. Without that
 * the two shared a row with no rule about who gave way, and a long label - "Hold
 * a word again for more about it" - ran on under the switch.
 *
 * [badge] is for a mark beside the label - see [BetaToggle], which is this
 * wearing one.
 */
@Composable
internal fun SettingsToggle(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    badge: (@Composable () -> Unit)? = null,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Space.xs),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    // Only as much of the row as it needs, so a badge beside it
                    // keeps its own width rather than being pushed off the end.
                    modifier = Modifier.weight(1f, fill = false),
                )

                badge?.invoke()
            }

            Spacer(modifier = Modifier.width(Space.md))

            // The label is put on the switch rather than on the row, because the
            // switch is the whole control: without this a screen reader would
            // reach a switch with a state and no name, and the words naming it
            // would be text it had already passed.
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                modifier = Modifier.semantics { contentDescription = label },
            )
        }

        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A switch for something that is not finished, and says so.
 *
 * The label carries the word rather than the section, because these sit beside
 * each other and a reader turning one on is being told about that one. What the
 * beta actually means - slow, and it spends money a screenful at a time - stays
 * as a visible [detail] rather than going behind a mark: it is the one thing
 * about these that somebody has to read before deciding, not after.
 */
@Composable
internal fun BetaToggle(
    label: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    SettingsToggle(label = label, detail = detail, checked = checked, onChange = onChange) {
        BetaBadge(modifier = Modifier.padding(start = Space.sm))
    }
}

/**
 * The mark that says a setting or a whole group is new and known to be rough.
 *
 * One badge for both, because they say the same thing: everything it is put on
 * runs through a model somebody has to set up and pay for, and none of it is
 * finished. See [SettingsSection]'s `beta`.
 */
@Composable
internal fun BetaBadge(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.settings_beta),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Space.xs, vertical = 1.dp),
    )
}

/** A caption over one field, for a group holding several of them. */
@Composable
internal fun LabelledField(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        content()
    }
}

/**
 * One group of settings, in the style of an iOS grouped list.
 *
 * The layout itself is [GroupSection], which the statistics page is built from too.
 * This stays as the name the settings screens call it by, and as the one place
 * that knows a settings group's surface is passed as `groupColor`.
 */
@Composable
internal fun SettingsSection(
    title: String,
    groupColor: Color,
    modifier: Modifier = Modifier,
    footnote: String? = null,
    /** Rows that draw their own vertical padding ask for none of the card's. */
    padding: Dp = Space.lg,
    /**
     * What this group needs a paragraph to say, behind a mark beside its name.
     *
     * Almost every group here has one. A settings page is opened to change one
     * thing, and every group explaining itself in prose at the same time is a
     * page that has to be read before it can be used. [footnote] is what is
     * left: a line so short that hiding it would cost more than showing it.
     */
    info: String? = null,
    /**
     * Whether this whole group is the unfinished kind - see [BetaBadge].
     *
     * A group rather than a switch, because what is in beta here is not one
     * setting but everything that goes through a model: the service, and the two
     * ways of asking it for more of the book than the reader pointed at. Marking
     * each switch and leaving the group unmarked said it three times and never
     * about the thing they have in common.
     */
    beta: Boolean = false,
    content: @Composable () -> Unit,
) {
    GroupSection(
        title = title,
        modifier = modifier,
        color = groupColor,
        footnote = footnote,
        padding = padding,
        badge = if (beta) {
            { BetaBadge(modifier = Modifier.padding(start = Space.sm)) }
        } else {
            null
        },
        info = info?.let {
            {
                InfoButton(
                    label = stringResource(Res.string.action_explain, title),
                    text = it,
                )
            }
        },
        content = content,
    )
}
