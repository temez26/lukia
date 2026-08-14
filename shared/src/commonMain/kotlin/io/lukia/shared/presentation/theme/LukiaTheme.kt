package io.lukia.shared.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Generous, evenly stepped corner radii in the spirit of Apple's rounded rects. */
internal val LukiaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun LukiaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) LukiaDarkColorScheme else LukiaLightColorScheme
    val glassStyle = remember { platformGlassStyle() }

    // Measured once at the root so every screen can lay itself out against the
    // real window size instead of assuming a phone.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthClass = WindowWidthClass.fromWidth(maxWidth)

        CompositionLocalProvider(
            LocalLukiaDimens provides dimensFor(widthClass),
            LocalWindowWidthClass provides widthClass,
            LocalGlassStyle provides glassStyle
        ) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = LukiaTypography,
                shapes = LukiaShapes,
                content = content
            )
        }
    }
}
