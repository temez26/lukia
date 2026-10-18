package io.lukia.features.reader.infrastructure.text.html

import io.lukia.features.reader.domain.text.BlockAlignment

/** What the innermost open element makes the text inside it. */
internal enum class BlockKind {
    Paragraph,
    Heading,
    ListItem,
    Quote,
    Code,
}

internal class OpenBlock(
    val tag: String,
    val kind: BlockKind,
    /** Only meaningful for [BlockKind.Heading]. */
    val headingLevel: Int = 0,
    /** Only meaningful for [BlockKind.ListItem]. */
    val marker: String = "",
    /** What the book asked for, here or on something this sits inside. */
    val alignment: BlockAlignment? = null,
    /** A heading only for how the stylesheet sets it - see [headingClasses]. */
    val styled: Boolean = false,
)
