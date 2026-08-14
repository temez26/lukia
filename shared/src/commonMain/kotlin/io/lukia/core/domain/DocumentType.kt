package io.lukia.core.domain

enum class DocumentType{
    PDF,
    EPUB,
    UNKNOWN;

    companion object {
        /**
         * The single place that decides which extensions the library
         * understands. Every platform analyzer and the folder scanners share it
         * so a picked file and a scanned one are always classified the same way.
         */
        fun fromExtension(extension: String): DocumentType =
            when (extension.lowercase()) {
                "pdf" -> PDF
                "epub" -> EPUB
                else -> UNKNOWN
            }
    }
}
