package io.lukia.shared.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The page's own colours, kept by anything that recolours what is drawn on it -
 * Home's top, in the colour of the book being read - so that a menu opened from
 * there is still drawn as the menus everywhere else are. Null where nothing has
 * recoloured the page.
 */
private val LocalPageColors = staticCompositionLocalOf<ColorScheme?> { null }

/** Recolours [content] with [colors], keeping the page's own for [OnPageColors]. */
@Composable
fun RecolouredForPage(colors: ColorScheme, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPageColors provides (LocalPageColors.current ?: MaterialTheme.colorScheme)) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}

/** Draws [content] - a menu or a sheet - in the page's own colours, whatever it was opened from. */
@Composable
fun OnPageColors(content: @Composable () -> Unit) {
    val page = LocalPageColors.current
    if (page == null) content() else MaterialTheme(colorScheme = page, content = content)
}
