package io.lukia.shared.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lukia.features.import.domain.CoverImage
import org.jetbrains.compose.resources.decodeToImageBitmap

/** Standard trade-paperback proportions, used for every cover in the app. */
const val BOOK_COVER_ASPECT_RATIO = 0.68f

@Composable
internal fun rememberCoverBitmap(coverImage: CoverImage?): ImageBitmap? {
    return remember(coverImage) {
        coverImage?.let { runCatching { it.bytes.decodeToImageBitmap() }.getOrNull() }
    }
}

/**
 * Muted, book-spine-like hues. Deliberately desaturated so a wall of generated
 * covers stays calm rather than turning into a colour clash.
 */
private val bookCoverPalette = listOf(
    Color(0xFF4A5D7E),
    Color(0xFF6B4E71),
    Color(0xFF3F6C5F),
    Color(0xFF8C5A44),
    Color(0xFF54607A),
    Color(0xFF7A4F5A),
    Color(0xFF41665C),
    Color(0xFF6E5B8B)
)

internal fun bookCoverColor(seed: String): Color {
    val hash = if (seed.hashCode() == Int.MIN_VALUE) 0 else kotlin.math.abs(seed.hashCode())
    return bookCoverPalette[hash % bookCoverPalette.size]
}

/**
 * A book cover: the embedded artwork when the file had one, otherwise a
 * generated monogram tile. A monogram reads as intentional design in a way a
 * repeated generic icon does not.
 */
@Composable
fun BookCover(
    coverImage: CoverImage?,
    title: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    elevation: Dp = 8.dp,
    monogramFontSize: androidx.compose.ui.unit.TextUnit = 34.sp
) {
    val coverBitmap = rememberCoverBitmap(coverImage)
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius)
    val baseColor = bookCoverColor(title)

    Box(
        modifier = modifier
            .aspectRatio(BOOK_COVER_ASPECT_RATIO)
            .shadow(elevation = elevation, shape = shape, clip = false)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        lerp(baseColor, Color.White, 0.12f),
                        lerp(baseColor, Color.Black, 0.22f)
                    )
                )
            )
            // A faint inner edge keeps light covers from bleeding into a light page.
            .border(
                width = 1.dp,
                color = Color.Black.copy(alpha = 0.08f),
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (coverBitmap != null) {
            Image(
                bitmap = coverBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = title.trim().firstOrNull()?.uppercase() ?: "?",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = monogramFontSize,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = monogramFontSize,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}
