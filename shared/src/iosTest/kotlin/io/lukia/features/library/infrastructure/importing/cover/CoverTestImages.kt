package io.lukia.features.library.infrastructure.importing.cover

import io.lukia.core.infrastructure.toByteArray
import io.lukia.core.infrastructure.toNSData
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIGraphicsPDFRenderer
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIRectFill
import kotlin.random.Random

/**
 * The pictures the cover tests need, made with the frameworks the app itself
 * draws with - there is no `ImageIO.write` on this platform, and a cover has to
 * be real bytes before anything can be asked about shrinking it.
 */

/**
 * Artwork of [width] by [height], in random blocks rather than a flat colour: a
 * blank image compresses to nothing, and half of what these tests are about is
 * how much a stored cover weighs.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun noisyImage(width: Int, height: Int): ByteArray {
    val random = Random(width * height)

    UIGraphicsBeginImageContextWithOptions(
        CGSizeMake(width.toDouble(), height.toDouble()),
        true,
        1.0,
    )

    for (top in 0 until height step NOISE_BLOCK) {
        for (left in 0 until width step NOISE_BLOCK) {
            random.nextColour().setFill()
            UIRectFill(
                CGRectMake(
                    left.toDouble(),
                    top.toDouble(),
                    NOISE_BLOCK.toDouble(),
                    NOISE_BLOCK.toDouble(),
                ),
            )
        }
    }

    val image = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()

    return checkNotNull(UIImagePNGRepresentation(checkNotNull(image))).toByteArray()
}

/** A one page PDF, which is all a cover renderer ever looks at. */
@OptIn(ExperimentalForeignApi::class)
internal fun singlePagePdf(): ByteArray {
    val renderer = UIGraphicsPDFRenderer(bounds = CGRectMake(0.0, 0.0, PAGE_WIDTH, PAGE_HEIGHT))

    return renderer.PDFDataWithActions { context ->
        context?.beginPage()
        UIColor.lightGrayColor.setFill()
        UIRectFill(CGRectMake(0.0, 0.0, PAGE_WIDTH, PAGE_HEIGHT))
    }.toByteArray()
}

/** How large a picture is, read back out of the bytes it was written to. */
@OptIn(ExperimentalForeignApi::class)
internal fun longestEdgeOf(imageBytes: ByteArray): Int =
    UIImage(data = imageBytes.toNSData()).size.useContents { maxOf(width, height).toInt() }

private fun Random.nextColour(): UIColor = UIColor.colorWithRed(
    red = nextDouble(),
    green = nextDouble(),
    blue = nextDouble(),
    alpha = 1.0,
)

private const val NOISE_BLOCK = 16

private const val PAGE_WIDTH = 612.0
private const val PAGE_HEIGHT = 792.0
