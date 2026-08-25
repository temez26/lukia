package io.lukia.features.profile.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The size the avatar takes where it stands for the whole screen's subject. */
val ProfileAvatarSize: Dp = 84.dp

/**
 * The reader, as a monogram on a disc in the app's accent.
 *
 * Initials rather than a picture, and the same decision - and the same reasoning
 * - as `BookCover`'s monogram tile: a generated mark reads as intentional design
 * where a generic silhouette reads as something missing. It also means the app
 * never asks for a photograph, a camera permission or a file, which for a
 * profile that exists to say "this is your reading" would all be beside the
 * point.
 *
 * Lit the way a cover is - one soft diagonal - so the disc sits on the same
 * surface as the books beside it rather than looking pasted onto them.
 */
@Composable
fun ProfileAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = ProfileAvatarSize,
    fontSize: TextUnit = 30.sp,
) {
    val accent = MaterialTheme.colorScheme.primary

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        lerp(accent, Color.White, 0.14f),
                        lerp(accent, Color.Black, 0.16f),
                    ),
                ),
            )
            // The letters are the name and the disc is decoration, so the
            // description says who rather than reading two capitals aloud.
            .semantics { contentDescription = "Profile picture" },
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
