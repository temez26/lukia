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
 * because it is the same question: what does this gesture do? A reader who only
 * ever looks words up turns the tap off here and stops translating sentences by
 * accident.
 *
 * Kept in the feature that owns the gesture rather than in the settings screen
 * that first drew it: the same control is now also in the reader's own panel,
 * where a reader who has just translated a paragraph they meant to skim can
 * change what their thumb does without leaving the book.
 */
@Composable
fun GestureControl(
    selected: PassageUnit?,
    onSelect: (PassageUnit?) -> Unit,
) {
    val choices = remember { listOf<PassageUnit?>(null) + PassageUnit.entries }

    SegmentedControl(
        options = choices,
        selected = selected,
        label = { it?.displayName() ?: stringResource(Res.string.gesture_off) },
        onSelect = onSelect,
    )
}

/**
 * How much of the book a gesture asks for, without the option of nothing.
 *
 * For the gesture whose off is somewhere else. Whether a tap translates is a
 * switch on the Translating page, beside the dictionary that answers a word; how
 * much it asks for is on the AI translation page, because only a model answers a
 * sentence. A control offering off in the second place would be a second page
 * turning off a switch set in the first - which is what the two did to each
 * other while they shared one nullable field.
 *
 * The word is the first choice rather than a missing one, and it is this row's
 * way of asking for nothing more than a word.
 */
@Composable
fun PassageUnitControl(
    selected: PassageUnit,
    onSelect: (PassageUnit) -> Unit,
) {
    SegmentedControl(
        options = PassageUnit.entries,
        selected = selected,
        label = { it.displayName() },
        onSelect = onSelect,
    )
}
