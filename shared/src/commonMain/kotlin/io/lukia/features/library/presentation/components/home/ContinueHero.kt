package io.lukia.features.library.presentation.components.home

import androidx.compose.animation.core.EaseInOut
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
import io.lukia.shared.presentation.theme.RecolouredForPage
import io.lukia.shared.presentation.theme.Space

/**
 * How far below the hero the book's colour takes to fade into the page: most of
 * the way to the middle of a phone's screen, behind the first shelf, so the
 * colour leaves rather than stops.
 */
private val FadeLength: Dp = 320.dp

/** The fade drawn in this many steps, each eased, so its start and end have no edge. */
private const val FadeSteps = 12

/** How much nearer black the colour is behind the header, so the controls there read on any cover. */
private const val HeaderDepth = 0.45f

/** How far below the status bar the deeper colour behind the header holds before it eases off. */
private val HeaderDeepLength: Dp = 64.dp

/** How far the deeper colour behind the header takes to ease into the book's own. */
private val HeaderEaseLength: Dp = 96.dp

/** Secondary text on the book's colour: light, a step back from the white beside it. */
private const val OnToneMuted = 0.85f

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
 * colour is always deepened for white text - see `rememberCoverTone`. Behind
 * the header it is deeper still, so the title there reads on any
 * cover. The fade is drawn past the hero's own bottom edge, under the items that
 * follow it. The ring and the button to carry on keep the app's accent.
 */
@Composable
internal fun ContinueHero(
    book: LibraryBook,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tone = rememberCoverTone(book.cover, book.document.bookTitle())
    val page = MaterialTheme.colorScheme.background
    val density = LocalDensity.current
    val fade = with(density) { FadeLength.toPx() }
    val deepEnd = WindowInsets.safeDrawing.getTop(density) + with(density) { HeaderDeepLength.toPx() }
    val easeEnd = deepEnd + with(density) { HeaderEaseLength.toPx() }
    val deep = lerp(tone, Color.Black, HeaderDepth)
    val onTone = MaterialTheme.colorScheme.copy(
        onBackground = Color.White,
        onSurface = Color.White,
        onSurfaceVariant = Color.White.copy(alpha = OnToneMuted),
        outlineVariant = Color.White.copy(alpha = OnToneLine),
    )

    RecolouredForPage(onTone) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(color = tone, size = size)
                    drawRect(color = deep, size = Size(size.width, deepEnd))
                    drawRect(
                        brush = Brush.verticalGradient(easedSteps(deep, tone), startY = deepEnd, endY = easeEnd),
                        topLeft = Offset(0f, deepEnd),
                        size = Size(size.width, easeEnd - deepEnd),
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            easedSteps(tone, page),
                            startY = size.height,
                            endY =
                            size.height + fade,
                        ),
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

/**
 * From one colour to another in evenly spaced steps, each eased: slow to leave
 * the first, slow to arrive at the second, so neither end of the gradient shows
 * an edge.
 */
private fun easedSteps(from: Color, to: Color): List<Color> = List(FadeSteps + 1) { step ->
    lerp(from, to, EaseInOut.transform(step / FadeSteps.toFloat()))
}
