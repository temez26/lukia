package io.lukia.shared.presentation.components.cover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.components.controls.ReadingProgressOverlay
import io.lukia.shared.presentation.theme.LukiaTheme
import io.lukia.shared.presentation.theme.Space

/**
 * A shelf's worth of covers with no artwork, which is what a shelf mostly is.
 *
 * The monogram tile is generated from the title, so the thing to look at is
 * three of them together: the colours have to differ enough to tell books apart
 * and stay light enough to read a letter off, in both themes and under the
 * chrome the app welds onto a cover.
 */
@Composable
private fun CoverGallery(dark: Boolean) {
    LukiaTheme(darkTheme = dark, touch = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.lg),
                modifier = Modifier.padding(Space.xl),
            ) {
                listOf("Dune", "Piranesi", "The Odyssey").forEach { title ->
                    Box(modifier = Modifier.width(CoverWidth)) {
                        BookCover(cover = null, title = title)

                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(MaterialTheme.shapes.medium),
                        ) {
                            ReadingProgressOverlay(fraction = 0.42f, percent = 42)
                        }

                        FavoriteBadge(
                            visible = title == "Piranesi",
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(Space.sm),
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun CoverGalleryLightPreview() = CoverGallery(dark = false)

@Preview
@Composable
private fun CoverGalleryDarkPreview() = CoverGallery(dark = true)

private val CoverWidth = 104.dp
