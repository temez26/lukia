package io.lukia.features.translate.presentation

import androidx.compose.runtime.Composable
import io.lukia.features.translate.domain.text.PassageUnit
import io.lukia.shared.presentation.components.controls.SegmentedControl

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
    SegmentedControl(
        options = GestureChoices,
        selected = selected,
        label = { it?.label ?: "Off" },
        onSelect = onSelect,
    )
}

private val GestureChoices: List<PassageUnit?> = listOf(null) + PassageUnit.entries
