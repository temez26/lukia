package io.lukia.features.library.presentation.components.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.presentation.bookTitle
import io.lukia.features.library.presentation.model.LibraryBook
import io.lukia.shared.presentation.components.cover.rememberCoverTone

/** How far, below what is drawn on it, the book's colour takes to fade into the page. */
private val FadeHeight: Dp = 56.dp

/** Secondary text on the book's colour: light, a step back from the white beside it. */
private const val OnToneMuted = 0.75f

/** Hairlines and the ring's track on the book's colour. */
private const val OnToneLine = 0.25f

/**
 * The top of Home while a book is being read, in that book's colour: from the
 * very top of the screen, behind the status bar and the header, down past the
 * book, then fading into the page - so the book in hand reads as the top of the
 * screen rather than as one more shelf among those under it.
 *
 * Its own top inset, since it is drawn under the status bar rather than below
 * it; what is laid on it is in light colours whatever the theme, because the
 * colour is always deepened for white text - see `rememberCoverTone`. Nothing
 * is laid over the fade, where the page's own colours take over again.
 */
@Composable
internal fun ContinueHero(
    book: LibraryBook,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tone = rememberCoverTone(book.cover, book.document.bookTitle())
    val page = MaterialTheme.colorScheme.background
    val fade = with(LocalDensity.current) { FadeHeight.toPx() }
    val onTone = MaterialTheme.colorScheme.copy(
        onBackground = Color.White,
        onSurface = Color.White,
        onSurfaceVariant = Color.White.copy(alpha = OnToneMuted),
        outlineVariant = Color.White.copy(alpha = OnToneLine),
    )

    MaterialTheme(colorScheme = onTone) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .drawBehind {
                    val solid = ((size.height - fade) / size.height).coerceIn(0f, 1f)
                    drawRect(Brush.verticalGradient(0f to tone, solid to tone, 1f to page))
                }
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(bottom = FadeHeight),
            content = content,
        )
    }
}
