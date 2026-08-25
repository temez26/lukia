package io.lukia.features.profile.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import io.lukia.features.profile.infrastructure.ProfileRepository
import io.lukia.features.profile.presentation.components.ProfileAvatar
import io.lukia.features.profile.presentation.components.ProfileAvatarButtonSize
import io.lukia.features.profile.presentation.components.ProfileFace
import io.lukia.features.profile.presentation.components.face
import io.lukia.shared.presentation.components.focusRing
import io.lukia.shared.presentation.components.scaleOnPress
import org.koin.compose.koinInject

/**
 * The way in to the reader's own page: their disc, wherever the app has a bar.
 *
 * A control of its own rather than a row in the actions menu. That menu is about
 * the library - importing books, tidying the shelf, the app's settings - and a
 * profile is not one of the things done to a shelf. It is also the one control
 * on a bar that shows something rather than saying it, which is exactly what an
 * avatar is for: a reader recognises their own initials faster than they read
 * the word "Profile".
 *
 * It takes the reader as a value and reaches for nothing. Every screen in this
 * app splits into a half that resolves dependencies and a half that draws, and a
 * control that injected would put a dependency graph behind every bar it sits on
 * - see [rememberProfileFace], which is the resolving half.
 */
@Composable
fun ProfileAvatarButton(
    face: ProfileFace,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = ProfileAvatarButtonSize,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .scaleOnPress(interactionSource)
            .clip(CircleShape)
            .focusRing(interactionSource, CircleShape)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = "Your profile",
                onClick = onClick,
            )
            // Merged and named here, so the disc underneath is announced as one
            // control rather than as a button containing a picture.
            .semantics(mergeDescendants = true) { contentDescription = "Your profile" },
    ) {
        ProfileAvatar(face = face, size = size)
    }
}

/**
 * The reader as the app currently knows them, for the screens that draw a bar.
 *
 * Resolved here rather than threaded down from the app's root: which reader is
 * using the app is nothing to do with a library or a home screen, and passing it
 * through both of them would be a parameter every screen carries and none of
 * them reads. This is the one place that asks, and what it hands back is a plain
 * value - so everything below it stays composable without a graph.
 *
 * The picture is asked for from here too. It is a database read, so it cannot
 * happen before the first frame; whichever bar draws the reader first sets it
 * going, and the ones after find it already read.
 */
@Composable
fun rememberProfileFace(): ProfileFace {
    val profiles = koinInject<ProfileRepository>()
    val profile by profiles.profile.collectAsState()
    val picture by profiles.picture.collectAsState()

    LaunchedEffect(profiles) {
        profiles.loadPicture()
    }

    return profile?.face(picture) ?: ProfileFace.Unknown
}
