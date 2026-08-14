package io.lukia.features.import.infrastructure.cover

import io.lukia.features.import.infrastructure.archive.ZipArchiveReader

/**
 * Extracts the cover image embedded in an EPUB archive, if one is declared.
 * Returns null whenever the archive is malformed or no cover can be located,
 * rather than throwing - a missing cover is an expected, non-fatal outcome.
 */
internal object EpubCoverExtractor {

    fun extract(epubBytes: ByteArray): ByteArray? {
        val zip = ZipArchiveReader(epubBytes)

        val opfPath = findOpfPath(zip) ?: return null
        val opfBytes = zip.readEntry(opfPath) ?: return null
        val opfXml = opfBytes.decodeToString()

        val opfDir = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
        val coverHref = findCoverHref(opfXml) ?: return null
        val coverPath = resolvePath(opfDir, coverHref)

        return zip.readEntry(coverPath)
    }

    private fun findOpfPath(zip: ZipArchiveReader): String? {
        val containerXml = zip.readEntry("META-INF/container.xml")?.decodeToString() ?: return null
        return Regex("""full-path\s*=\s*["']([^"']+)["']""")
            .find(containerXml)
            ?.groupValues
            ?.get(1)
            ?.let(::decodePercentEncoding)
    }

    private fun findCoverHref(opfXml: String): String? {
        val items = findElements(opfXml, "item").map { parseAttributes(it) }

        items.firstOrNull { it["properties"]?.contains("cover-image") == true }
            ?.get("href")
            ?.let { return decodePercentEncoding(it) }

        val coverId = findElements(opfXml, "meta")
            .map { parseAttributes(it) }
            .firstOrNull { it["name"] == "cover" }
            ?.get("content")

        if (coverId != null) {
            items.firstOrNull { it["id"] == coverId }
                ?.get("href")
                ?.let { return decodePercentEncoding(it) }
        }

        items.firstOrNull {
            it["id"]?.contains("cover", ignoreCase = true) == true &&
                it["media-type"]?.startsWith("image/") == true
        }?.get("href")?.let { return decodePercentEncoding(it) }

        return null
    }

    private fun findElements(xml: String, tagName: String): List<String> {
        return Regex("""<$tagName\b[^>]*/?>""").findAll(xml).map { it.value }.toList()
    }

    private fun parseAttributes(tag: String): Map<String, String> {
        val attributes = mutableMapOf<String, String>()
        Regex("""([\w:-]+)\s*=\s*"([^"]*)"|([\w:-]+)\s*=\s*'([^']*)'""").findAll(tag).forEach { match ->
            val name = match.groupValues[1].ifEmpty { match.groupValues[3] }
            val value = if (match.groupValues[1].isNotEmpty()) match.groupValues[2] else match.groupValues[4]
            attributes[name] = value
        }
        return attributes
    }

    private fun resolvePath(baseDir: String, relativePath: String): String {
        val combined = if (baseDir.isEmpty()) relativePath else "$baseDir/$relativePath"
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

    private fun decodePercentEncoding(value: String): String {
        if ('%' !in value) return value

        val bytes = mutableListOf<Byte>()
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '%' && index + 2 < value.length) {
                val hex = value.substring(index + 1, index + 3)
                val byte = hex.toIntOrNull(16)
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
}
