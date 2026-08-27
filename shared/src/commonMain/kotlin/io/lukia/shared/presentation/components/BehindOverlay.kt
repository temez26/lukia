package io.lukia.shared.presentation.components

import androidx.compose.foundation.focusGroup
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.clearAndSetSemantics

/**
 * Puts a screen out of reach while an [OverlayPanel] is open over it.
 *
 * A panel that dims what is behind it looks modal, and until this was applied it
 * was only that: the scrim caught the pointer and nothing else. Tab walked
 * straight through the dimming into the library underneath, and a screen
 * reader's swipe-next left the panel after its last row and carried on through
 * every book on the shelf behind - with no way back and nothing to say the panel
 * was still open.
 *
 * Applied by whoever hosts the panel rather than by the panel, because in-window
 * there is no other way round. The panel is a sibling drawn after the content, and
 * a composable cannot reach out and quieten the ones beside it - which is exactly
 * why Material's own modal surfaces are `Dialog`s and `Popup`s, and why the app's
 * [ConfirmDialog] is left as a platform dialog. Those get a window of their own,
 * and a window is what makes everything outside it inert for free.
 *
 * The two halves answer the two ways in. Clearing the semantics takes the whole
 * subtree out of the accessibility tree, so assistive technology cannot reach it;
 * deactivating the focus group takes it out of focus search, so Tab and the arrow
 * keys cannot either. Pointer input needs nothing here - the scrim inside the
 * panel already covers the window and swallows it.
 */
fun Modifier.behindOverlay(open: Boolean): Modifier =
    if (!open) {
        this
    } else {
        // Order matters: the properties have to be declared before the group
        // they deactivate, or the group is made first and reads no properties.
        focusProperties { canFocus = false }
            .focusGroup()
            .clearAndSetSemantics {}
    }
