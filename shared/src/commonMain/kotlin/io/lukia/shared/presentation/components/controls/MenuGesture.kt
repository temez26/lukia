package io.lukia.shared.presentation.components.controls

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import io.lukia.getPlatform
import io.lukia.resources.Res
import io.lukia.resources.book_actions
import io.lukia.shared.presentation.components.menu.MenuPointer
import org.jetbrains.compose.resources.stringResource

/**
 * The gesture that asks for the menu belonging to whatever is under it.
 *
 * Two gestures, because the two kinds of machine do not share one. A finger has
 * only the clock to say it means something other than a tap, so it holds. A
 * pointer has a second button - two fingers on a trackpad - which every desktop
 * already means "tell me about this" with, at once and without the wait.
 *
 * Neither is offered on the other's machine: a press and hold with a mouse is
 * half a second of nothing followed by a menu that could have been immediate,
 * and is not a gesture desktop users reach for.
 *
 * The haptic belongs to the hold alone. It is what tells a finger that it has
 * held long enough, and a mouse has nothing to be told.
 *
 * [touch] is a parameter rather than a straight lookup so a test can compose
 * either machine - the same reason [io.lukia.shared.presentation.theme.LukiaTheme]
 * takes its own.
 */
@Composable
fun Modifier.menuGesture(
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    menuLabel: String? = stringResource(Res.string.book_actions),
    clickLabel: String? = null,
    enabled: Boolean = true,
    role: Role? = null,
    touch: Boolean = getPlatform().isMobile,
): Modifier {
    val haptics = LocalHapticFeedback.current

    val hold: (() -> Unit)? = if (touch) {
        {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onMenu()
        }
    } else {
        null
    }

    return onSecondaryPress(enabled = enabled, onPress = onMenu).combinedClickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        onClickLabel = clickLabel,
        role = role,
        onClick = onClick,
        onLongClickLabel = menuLabel.takeIf { touch },
        onLongClick = hold,
    )
}

/**
 * A press of the second mouse button - a right click, or two fingers on a
 * trackpad, which the machine reports as the same thing.
 *
 * Answered on the press rather than the release, so the menu is open by the time
 * the second finger lifts. A light two-finger tap and a full click arrive
 * identically; which the trackpad sends is the system's setting, not this one's.
 *
 * Its own modifier rather than desktop's `onClick(PointerMatcher…)`, which
 * cannot be written in code the phone compiles too. Touch never reports a second
 * button, so this is silent there rather than conditional.
 */
@Composable
fun Modifier.onSecondaryPress(enabled: Boolean = true, onPress: () -> Unit): Modifier {
    val current by rememberUpdatedState(onPress)
    // Where this element is in the window, so the press can be told to the menu
    // it opens in the window's terms - see [MenuPointer].
    val placed = remember { arrayOfNulls<LayoutCoordinates>(1) }

    return onPlaced { placed[0] = it }.pointerInput(enabled) {
        if (!enabled) return@pointerInput

        awaitEachGesture {
            val event = awaitPointerEvent()
            val press = event.changes.firstOrNull { it.changedToDown() }

            if (press != null && event.buttons.isSecondaryPressed) {
                // Taken here so the click underneath does not also see it. It
                // ignores the second button anyway on every platform today, and
                // a gesture that relies on that is a gesture waiting to break.
                press.consume()
                placed[0]?.takeIf { it.isAttached }?.let { MenuPointer.record(it.localToWindow(press.position)) }
                current()
            }
        }
    }
}
