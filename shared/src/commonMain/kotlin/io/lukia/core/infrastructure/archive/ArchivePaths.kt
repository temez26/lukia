package io.lukia.core.infrastructure.archive

/**
 * Joins a relative href onto the directory it was written in, resolving `.` and
 * `..` so the result addresses a ZIP entry by its literal name.
 */
internal fun resolveArchivePath(baseDirectory: String, relativePath: String): String {
    // An href that is already anchored at the archive root ignores where it was
    // written; a fragment addresses a place inside a file, not a file.
    val href = relativePath.substringBefore('#').removePrefix("/")
    val combined = if (baseDirectory.isEmpty()) href else "$baseDirectory/$href"

    val segments = mutableListOf<String>()

    combined.split('/').forEach { segment ->
        when (segment) {
            "", "." -> Unit
            ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
            else -> segments.add(segment)
        }
    }

    return segments.joinToString("/")
}

/**
 * Hrefs inside an EPUB are URLs, so a chapter called `Chapter 1.xhtml` is
 * written `Chapter%201.xhtml` - while the ZIP entry it names keeps the space.
 */
internal fun decodePercentEncoding(value: String): String {
    if ('%' !in value) return value

    val bytes = mutableListOf<Byte>()
    var index = 0

    while (index < value.length) {
        val char = value[index]

        if (char == '%' && index + 2 < value.length) {
            val byte = value.substring(index + 1, index + 3).toIntOrNull(16)

            if (byte != null) {
                bytes.add(byte.toByte())
                index += 3
                continue
            }
        }

        bytes.addAll(char.toString().encodeToByteArray().toList())
        index++
    }

    return bytes.toByteArray().decodeToString()
}
