package io.lukia.features.translate.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.lukia.core.domain.text.PassageUnit
import io.lukia.core.presentation.displayName
import io.lukia.resources.Res
import io.lukia.resources.gesture_off
import io.lukia.shared.presentation.components.controls.SegmentedControl
import org.jetbrains.compose.resources.stringResource

/**
 * What one gesture asks for, including nothing at all.
 *
 * Off is a choice in the same row as the units rather than a switch beside them,
 * because it is the same question: what does this gesture do?
 *
 * Kept in the feature that owns the gesture rather than in the settings screen
 * that first drew it - the same control is now in the reader's own panel too,
 * where somebody who has just translated a paragraph they meant to skim can
 * change what their thumb does without leaving the book.
 */
@Composable
fun GestureControl(
    selected: PassageUnit?,
    onSelect: (PassageUnit?) -> Unit,
    /**
     * Which units this control may be set to, off aside.
     *
     * A parameter because the answer differs by where the control is: all three
     * in the reader's own panel, and under "asking for more than a word" only
     * the two that are more than a word - offering the word there would be a row
     * whose name and choices disagreed.
     *
     * A gesture set to a unit this control does not offer leaves nothing
     * selected, which is honest and is what [SegmentedControl] already does for
     * a page width between two presets.
     */
    units: List<PassageUnit> = PassageUnit.entries,
) {
    val choices = remember(units) { listOf<PassageUnit?>(null) + units }

    SegmentedControl(
        options = choices,
        selected = selected,
        label = { it?.displayName() ?: stringResource(Res.string.gesture_off) },
        onSelect = onSelect,
    )
}
