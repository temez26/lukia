package io.lukia.shared.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import io.lukia.core.domain.AppAccent
import io.lukia.core.domain.AppPalette
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.fail

/**
 * That every pairing the app actually draws can be read.
 *
 * Contrast is the one part of a colour scheme that cannot be judged by looking:
 * a grey that reads perfectly well to whoever chose it is the same grey somebody
 * else cannot see at all, and the difference between the two is arithmetic. This
 * app had a secondary label at 2.8:1 against its own recessed fill, and white
 * button text at 2.0:1 on the green a reader can pick in Settings.
 *
 * Held to WCAG 2.2 AA - 4.5:1 for text and 3:1 for a control's own boundary -
 * across every palette, both lightnesses and every accent, because all of those
 * are choices a reader makes rather than states the app is only sometimes in.
 */
class ContrastTest {

    private val schemes: List<Triple<String, ColorScheme, Boolean>> =
        AppPalette.entries.flatMap { palette ->
            listOf(false, true).flatMap { dark ->
                AppAccent.entries.map { accent ->
                    Triple(
                        "$palette ${if (dark) "dark" else "light"} on $accent",
                        colorSchemeFor(palette, dark, accent),
                        dark,
                    )
                }
            }
        }

    @Test
    fun secondaryTextIsReadableOnEverySurfaceItIsSetOn() {
        // `onSurfaceVariant` is not only a caption colour here - `QuoteBlock`
        // sets whole quoted paragraphs of a book in it.
        assertAll { name, scheme, _ ->
            listOf(
                "$name: onSurfaceVariant on background" to
                    contrast(scheme.onSurfaceVariant, scheme.background),
                "$name: onSurfaceVariant on surface" to
                    contrast(scheme.onSurfaceVariant, scheme.surface),
                "$name: onSurfaceVariant on surfaceVariant" to
                    contrast(scheme.onSurfaceVariant, scheme.surfaceVariant),
            ).failing(TextContrast)
        }
    }

    @Test
    fun bodyTextIsReadableOnEverySurface() {
        assertAll { name, scheme, _ ->
            listOf(
                "$name: onSurface on background" to contrast(scheme.onSurface, scheme.background),
                "$name: onSurface on surface" to contrast(scheme.onSurface, scheme.surface),
                "$name: onSurface on surfaceVariant" to
                    contrast(scheme.onSurface, scheme.surfaceVariant),
            ).failing(TextContrast)
        }
    }

    /** The label on a filled button, which has nowhere else to go. */
    @Test
    fun aFilledControlCanBeLabelled() {
        assertAll { name, scheme, _ ->
            listOf(
                "$name: onPrimary on primary" to contrast(scheme.onPrimary, scheme.primary),
                "$name: onError on error" to contrast(scheme.onError, scheme.error),
            ).failing(TextContrast)
        }
    }

    /** The accent as text, which is what a ghost button and every tinted label is. */
    @Test
    fun theAccentCanBeWrittenIn() {
        assertAll { name, scheme, _ ->
            listOf(
                "$name: primary on surface" to contrast(scheme.primary, scheme.surface),
                "$name: primary on background" to contrast(scheme.primary, scheme.background),
                "$name: primary on surfaceVariant" to
                    contrast(scheme.primary, scheme.surfaceVariant),
                "$name: error on surface" to contrast(scheme.error, scheme.surface),
            ).failing(TextContrast)
        }
    }

    /**
     * The focus ring, which is the only thing telling a keyboard where it is,
     * and the secondary button's hairline, which is the only thing saying where
     * the button is. Both are boundaries rather than text, so 3:1.
     */
    @Test
    fun aBoundaryCanBeSeen() {
        assertAll { name, scheme, _ ->
            val border = scheme.onSurface.copy(alpha = LukiaAlpha.Border).over(scheme.surface)

            listOf(
                "$name: focus ring on surface" to contrast(scheme.primary, scheme.surface),
                "$name: focus ring on surfaceVariant" to
                    contrast(scheme.primary, scheme.surfaceVariant),
                "$name: secondary button edge on surface" to contrast(border, scheme.surface),
            ).failing(NonTextContrast)
        }
    }

    private fun assertAll(check: (String, ColorScheme, Boolean) -> List<String>) {
        val failures = schemes.flatMap { (name, scheme, dark) -> check(name, scheme, dark) }

        if (failures.isNotEmpty()) {
            fail("A pairing the app draws cannot be read:\n" + failures.sorted().joinToString("\n"))
        }
    }

    private fun List<Pair<String, Double>>.failing(minimum: Double) =
        filter { it.second < minimum }
            .map { "${it.first} is ${format(it.second)}:1, under $minimum:1" }

    private fun format(ratio: Double) = (ratio * 100).toInt() / 100.0

    private companion object {
        const val TextContrast = 4.5
        const val NonTextContrast = 3.0
    }
}

/** This colour laid over an opaque one, which is what an alpha actually draws as. */
private fun Color.over(background: Color) = Color(
    red = red * alpha + background.red * (1 - alpha),
    green = green * alpha + background.green * (1 - alpha),
    blue = blue * alpha + background.blue * (1 - alpha),
)

/**
 * WCAG's own definition, which is a ratio of relative luminances offset by 0.05
 * so that black against black is 1 rather than a division by zero.
 */
private fun contrast(a: Color, b: Color): Double {
    val first = a.relativeLuminance()
    val second = b.relativeLuminance()

    return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
}

private fun Color.relativeLuminance(): Double {
    fun channel(value: Float): Double {
        val v = value.toDouble()

        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
}
