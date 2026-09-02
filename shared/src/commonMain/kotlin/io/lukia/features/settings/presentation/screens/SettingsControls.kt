package io.lukia.features.settings.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.components.surface.GroupSection
import io.lukia.shared.presentation.theme.Space

// The parts a settings page is built from. None of them knows what a setting
// means - each takes a value, shows it, and reports a change.

/**
 * The parts of the app somebody comes to Settings to change.
 *
 * Four rather than the nine sections behind them, because a settings page is
 * read by somebody who already knows what they want to change and is looking for
 * where it lives. Nine equally-weighted boxes in one column is a list to search;
 * four segments across the top is a question with an answer.
 *
 * The order is how often they are opened, which is also how much machinery each
 * one asks the reader to think about: what the app looks like, how books are
 * set, what is translated, and last of all who is doing it.
 */
enum class SettingsArea(val title: String) {

    Appearance("Appearance"),

    /** How a book is set: its page, its type, and what a PDF opens as. */
    Reading("Reading"),

    /** What is translated, when, and into what. */
    Translating("Translating"),

    /**
     * Who answers - the translator, and whatever looks up a single word.
     *
     * Apart from Translating because it is the only group that can be filled in
     * wrongly, and the only one whose settings are about machinery rather than
     * about reading. Somebody changing the language they read into should not
     * have to scroll past an API key to reach it.
     */
    Providers("Providers"),
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
 * A switch for one setting, with the sentence that says what it does under it.
 *
 * The detail is not optional. Everything on this screen that is on or off is
 * something whose name alone does not settle whether you want it, which is why
 * these are switches with prose rather than rows in a list.
 *
 * [badge] is for a mark beside the label - see [BetaToggle], which is this
 * wearing one.
 */
@Composable
internal fun SettingsToggle(
    label: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    badge: (@Composable () -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                badge?.invoke()
            }

            Switch(checked = checked, onCheckedChange = onChange)
        }

        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

@Composable
private fun BetaBadge(modifier: Modifier = Modifier) {
    Text(
        text = "BETA",
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
    content: @Composable () -> Unit,
) {
    GroupSection(
        title = title,
        modifier = modifier,
        color = groupColor,
        footnote = footnote,
        padding = padding,
        content = content,
    )
}
