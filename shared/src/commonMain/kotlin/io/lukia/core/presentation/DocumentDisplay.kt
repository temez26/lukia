package io.lukia.core.presentation

import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType

fun DocumentInfo.displayTitle(): String {
    val fileName = document.fileName
    val extension = document.extension

    return if (extension.isNotBlank() && fileName.endsWith(".$extension", ignoreCase = true)) {
        fileName.dropLast(extension.length + 1)
    } else {
        fileName
    }
}

fun DocumentInfo.displayType(): String = type.displayName()

/**
 * What to call a format where there is no document to hand - a filter offering
 * the formats a library holds, rather than a card describing one book.
 */
fun DocumentType.displayName(): String = when (this) {
    DocumentType.PDF -> "PDF"
    DocumentType.EPUB -> "EPUB"
    DocumentType.DOCX -> "Word"
    DocumentType.TEXT -> "Text"
    DocumentType.MARKDOWN -> "Markdown"
    DocumentType.UNKNOWN -> "Unknown"
}

fun DocumentInfo.formattedSize(): String = formatFileSize(sizeBytes)

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"

    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var unitIndex = 0

    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }

    val rounded = (value * 10).let { kotlin.math.round(it) } / 10
    val formatted = if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        rounded.toString()
    }

    return "$formatted ${units[unitIndex]}"
}
