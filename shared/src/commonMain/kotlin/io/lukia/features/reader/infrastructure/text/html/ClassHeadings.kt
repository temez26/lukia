package io.lukia.features.reader.infrastructure.text.html

/**
 * What a converted book writes its headings as. Kindle conversions set every
 * heading as a `div class="heading_…"` sized by the stylesheet, which this does
 * not read - so without this those books' chapter titles came out as paragraphs.
 */
internal val ClassHeadingElements = setOf("p", "div")

/**
 * A heading whose tag does not say how deep it is. Second, as a book's titles
 * most often are: a part or the book's own title is the first.
 */
internal const val CLASS_HEADING_LEVEL = 2

internal fun String?.namesAHeading(): Boolean =
    this?.split(' ')?.any { it.startsWith("heading", ignoreCase = true) } == true

/** One rule of a stylesheet: what it selects, and what it sets. */
private val CssRule = Regex("""([^{}]+)\{([^{}]*)\}""")

private val CssComment = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)

/** A selector of one class, on its own or on one element: `.sub`, `p.sub`. */
private val ClassSelector = Regex("""^(?:[a-zA-Z][\w-]*)?\.([\w-]+)$""")

/** Set at least this much larger than the text, and bold, a class reads as a heading. */
private const val SUB_HEADING_SCALE = 1.15f

/** And at this much larger, as a heading a chapter is divided by rather than a sub-heading. */
private const val HEADING_SCALE = 1.5f

private const val BOLD_WEIGHT = 600

/**
 * The classes a book's stylesheets set as headings, with the level each reads as.
 *
 * Plenty of books - converted ones above all - never use a heading tag below the
 * chapter title: their sections are `div class="calibre12"`, and only the
 * stylesheet makes them bold and large. Every other reader draws that stylesheet,
 * so their readers see headings; this one does not, so it reads the one thing a
 * heading is made of - set bold, and larger than the text - and nothing else.
 *
 * Only simple selectors are read, a class alone or on one element. A rule that
 * needs the page's structure to apply is not a heading style anyone writes.
 */
internal fun headingClasses(css: String): Map<String, Int> = buildMap {
    CssRule.findAll(css.replace(CssComment, "")).forEach { rule ->
        val level = headingLevelOf(rule.groupValues[2]) ?: return@forEach

        rule.groupValues[1].split(',').forEach { selector ->
            ClassSelector.matchEntire(selector.trim())?.let { put(it.groupValues[1], level) }
        }
    }
}

private fun headingLevelOf(declarations: String): Int? {
    val properties = declarations.split(';').mapNotNull { declaration ->
        val parts = declaration.split(':', limit = 2)
        if (parts.size != 2) return@mapNotNull null
        parts[0].trim().lowercase() to parts[1].replace("!important", "").trim().lowercase()
    }.toMap()

    val weight = properties["font-weight"]
    val bold = weight != null && (weight.startsWith("bold") || (weight.toIntOrNull() ?: 0) >= BOLD_WEIGHT)
    val scale = properties["font-size"]?.let(::relativeSize)

    return when {
        !bold || scale == null -> null
        scale >= HEADING_SCALE -> 2
        scale >= SUB_HEADING_SCALE -> SUB_HEADING_LEVEL
        else -> null
    }
}

/** A heading the stylesheet makes smaller than a chapter's sections. */
private const val SUB_HEADING_LEVEL = 3

/** A font size against the text around it; null for one that says nothing about that, such as `14px`. */
private fun relativeSize(value: String): Float? = when {
    value.endsWith("rem") -> value.removeSuffix("rem").toFloatOrNull()
    value.endsWith("em") -> value.removeSuffix("em").toFloatOrNull()
    value.endsWith("%") -> value.removeSuffix("%").toFloatOrNull()?.div(PERCENT)
    else -> NamedSizes[value]
}

private const val PERCENT = 100f

private val NamedSizes = mapOf("large" to 1.2f, "larger" to 1.2f, "x-large" to 1.5f, "xx-large" to 2f)

/**
 * A heading read off the stylesheet is at most this long. A class that sets one
 * bold, large paragraph of ordinary text - a book's pull quotes, say - is not
 * a heading for having the look of one.
 */
internal const val MAX_STYLED_HEADING_LENGTH = 120
