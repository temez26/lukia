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
import io.lukia.resources.Res
import io.lukia.resources.action_explain
import io.lukia.resources.settings_beta
import io.lukia.shared.presentation.components.controls.InfoButton
import io.lukia.shared.presentation.components.controls.toggleRow
import io.lukia.shared.presentation.components.surface.GroupSection
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

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
enum class SettingsArea {

    Appearance,

    /** How a book is set: its page, its type, and what a PDF opens as. */
    Reading,

    /** What is translated, when, and into what. */
    Translating,

    /**
     * Who answers - the translator, and whatever looks up a single word.
     *
     * Apart from Translating because it is the only group that can be filled in
     * wrongly, and the only one whose settings are about machinery rather than
     * about reading. Somebody changing the language they read into should not
     * have to scroll past an API key to reach it.
     */
    Providers,
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
 * Two ways of explaining one, and a setting should use exactly one of them.
 * [detail] is a line short enough to be read whether or not anybody wanted it,
 * and is for the settings that cost money or throw something away - a warning
 * nobody asked for is still a warning worth giving. [info] is the paragraph
 * behind a mark, for everything else.
 *
 * The mark sits at the end of the row rather than beside the label, and that is
 * not a matter of taste: the row is the control - see [toggleRow] - so it merges
 * everything inside it into one thing a screen reader announces. A button placed
 * in there would be drawn but not reachable. Outside the row it stays a control
 * of its own.
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
    info: String? = null,
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
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .weight(1f)
                    .toggleRow(label = label, checked = checked, onChange = onChange),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    badge?.invoke()
                }

                Switch(checked = checked, onCheckedChange = null)
            }

            if (info != null) {
                InfoButton(
                    label = stringResource(Res.string.action_explain, label),
                    text = info,
                    modifier = Modifier.padding(start = Space.xs),
                )
            }
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

@Composable
private fun BetaBadge(modifier: Modifier = Modifier) {
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
    content: @Composable () -> Unit,
) {
    GroupSection(
        title = title,
        modifier = modifier,
        color = groupColor,
        footnote = footnote,
        padding = padding,
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
