package io.lukia.shared.presentation.components.state

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.theme.LukiaTheme
import io.lukia.shared.presentation.theme.Space

/**
 * What a screen shows while it has nothing to show, in both themes.
 *
 * These two are drawn into the same slot by [UiStateContent], so the thing worth
 * looking at is that they are the same width, the same weight and centred the
 * same way - which they were not until they were put beside each other.
 */
@Composable
private fun StateGallery(dark: Boolean) {
    LukiaTheme(darkTheme = dark, touch = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Space.xxl),
                modifier = Modifier.padding(Space.xl).width(GalleryWidth),
            ) {
                LoadingState(
                    message = "Reading the folder",
                    modifier = Modifier.fillMaxWidth(),
                )

                ErrorState(
                    title = "That book will not open",
                    message = "The file has moved or is no longer readable.",
                    actionLabel = "Try again",
                    onAction = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Preview
@Composable
private fun StateGalleryLightPreview() = StateGallery(dark = false)

@Preview
@Composable
private fun StateGalleryDarkPreview() = StateGallery(dark = true)

private val GalleryWidth = 360.dp
