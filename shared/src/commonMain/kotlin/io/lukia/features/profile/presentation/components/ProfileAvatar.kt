package io.lukia.features.profile.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lukia.features.profile.domain.ProfileAvatar
import io.lukia.features.profile.domain.ProfilePicture
import io.lukia.features.profile.domain.ProfileTint
import io.lukia.features.profile.domain.UserProfile
import io.lukia.shared.presentation.theme.LukiaMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/** The size the disc takes where it stands for the whole screen's subject. */
val ProfileAvatarSize: Dp = 84.dp

/** ...and where it is one control among others on a bar. */
val ProfileAvatarButtonSize: Dp = 30.dp

/**
 * How much of the disc the initials take up.
 *
 * Two capitals at this share sit inside the circle with room to breathe at every
 * size the disc is drawn at, which is what a fixed point size cannot do.
 */
private const val MonogramShare = 0.36f

/**
 * How the reader is drawn, gathered into one value.
 *
 * The picture travels beside the choice rather than inside it because the two
 * are read from different places at different speeds - see `ProfileRepository`.
 * Passing them as one thing is what lets every disc in the app take a single
 * parameter and lets a test compose one without a dependency graph.
 */
@Immutable
data class ProfileFace(
    val initials: String,
    val avatar: ProfileAvatar = ProfileAvatar.Monogram,
    val tint: ProfileTint = ProfileTint.Accent,
    /** Null while it is still being read, and for a reader who has never set one. */
    val picture: ProfilePicture? = null,
) {
    companion object {

        /** What a disc shows before the profile behind it has been read. */
        val Unknown = ProfileFace(initials = "?")
    }
}

/** The reader as they have chosen to appear, from what is stored about them. */
fun UserProfile.face(picture: ProfilePicture? = null): ProfileFace = ProfileFace(
    initials = initials,
    avatar = avatar,
    tint = tint,
    picture = picture,
)

/**
 * The reader, on a tinted disc.
 *
 * Initials are the default, and the same decision - and the same reasoning - as
 * `BookCover`'s monogram tile: a generated mark reads as intentional design
 * where a generic silhouette reads as something missing. A reader who would
 * rather not be a pair of letters can take one of the app's own marks, or a
 * picture of their own, and the disc is the same disc underneath all three.
 *
 * The disc is lit the way a cover is - one soft diagonal - so it sits on the
 * same surface as the books beside it rather than looking pasted onto them. A
 * picture covers the light entirely, which is what a picture is for.
 */
@Composable
fun ProfileAvatar(
    face: ProfileFace,
    modifier: Modifier = Modifier,
    size: Dp = ProfileAvatarSize,
) {
    val colors = tintColors(face.tint)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        lerp(colors.fill, Color.White, 0.14f),
                        lerp(colors.fill, Color.Black, 0.16f),
                    ),
                ),
            )
            // The letters are the name and the disc is decoration, so this says
            // who rather than reading two capitals aloud.
            .semantics { contentDescription = "Profile picture" },
    ) {
        AvatarContent(face = face, content = colors.content, size = size)
    }
}

/**
 * What is on the disc.
 *
 * A picture that has been asked for but has not arrived - or has since been
 * removed - falls back to the monogram rather than to an empty circle, for the
 * same reason a book with no artwork gets a letter instead of a grey rectangle.
 */
@Composable
private fun AvatarContent(
    face: ProfileFace,
    content: Color,
    size: Dp,
) {
    val picture = face.picture

    when {
        face.avatar is ProfileAvatar.Picture && picture != null -> AvatarPicture(picture)

        face.avatar is ProfileAvatar.Symbol -> ProfileSymbolGlyph(
            symbol = face.avatar.symbol,
            tint = content,
            modifier = Modifier.fillMaxSize(),
        )

        else -> Text(
            text = face.initials,
            style = MaterialTheme.typography.titleLarge.copy(
                // Set as a fraction of the disc rather than from the type scale,
                // so one drawing serves a toolbar and a profile page both.
                fontSize = (size.value * MonogramShare).sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = content,
        )
    }
}

/**
 * The reader's own picture, decoded off the main thread and faded in.
 *
 * Cropped rather than fitted: a disc has one shape and photographs do not, and
 * the alternative is a portrait with the tint showing above and below it. The
 * middle of a picture somebody chose of themselves is the part they meant.
 */
@Composable
private fun AvatarPicture(picture: ProfilePicture) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, picture) {
        value = withContext(Dispatchers.Default) {
            // A picture that will not decode is a picture the reader does not
            // have, from their side - so it is not an error to report, and the
            // monogram underneath is already the right answer.
            runCatching { picture.bytes.decodeToImageBitmap() }.getOrNull()
        }
    }

    val shown by animateFloatAsState(
        targetValue = if (bitmap == null) 0f else 1f,
        animationSpec = LukiaMotion.standard(),
        label = "avatarPicture",
    )

    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = shown },
        )
    }
}
