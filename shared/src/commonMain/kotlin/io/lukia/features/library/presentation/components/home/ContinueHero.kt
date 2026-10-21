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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.presentation.bookTitle
import io.lukia.features.library.presentation.model.LibraryBook
import io.lukia.shared.presentation.components.cover.rememberCoverTone
import io.lukia.shared.presentation.theme.Space

/**
 * How far below the hero the book's colour takes to fade into the page: most of
 * the way to the middle of a phone's screen, behind the first shelf, so the
 * colour leaves rather than stops.
 */
private val FadeLength: Dp = 320.dp

/** The fade drawn in this many steps, each eased, so its start and end have no edge. */
private const val FadeSteps = 12

/** Secondary text on the book's colour: light, a step back from the white beside it. */
private const val OnToneMuted = 0.75f

/** Hairlines and the ring's track on the book's colour. */
private const val OnToneLine = 0.25f

/**
 * The top of Home while a book is being read, in that book's colour: from the
 * very top of the screen, behind the status bar and the header, down past the
 * book, then fading slowly into the page behind the shelves under it - so the
 * book in hand reads as the top of the screen rather than as one more shelf.
 *
 * Its own top inset, since it is drawn under the status bar rather than below
 * it; what is laid on it is in light colours whatever the theme, because the
 * colour is always deepened for white text - see `rememberCoverTone`. The fade
 * is drawn past the hero's own bottom edge, under the items that follow it.
 */
@Composable
internal fun ContinueHero(
    book: LibraryBook,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tone = rememberCoverTone(book.cover, book.document.bookTitle())
    val page = MaterialTheme.colorScheme.background
    val fade = with(LocalDensity.current) { FadeLength.toPx() }
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
                    drawRect(color = tone, size = size)

                    // Evenly spaced steps, each eased by a smoothstep: slow to
                    // leave the colour, slow to arrive at the page.
                    val steps = List(FadeSteps + 1) { step ->
                        val t = step / FadeSteps.toFloat()
                        lerp(tone, page, t * t * (3 - 2 * t))
                    }
                    drawRect(
                        brush = Brush.verticalGradient(steps, startY = size.height, endY = size.height + fade),
                        topLeft = Offset(0f, size.height),
                        size = Size(size.width, fade),
                    )
                }
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(bottom = Space.lg),
            content = content,
        )
    }
}
