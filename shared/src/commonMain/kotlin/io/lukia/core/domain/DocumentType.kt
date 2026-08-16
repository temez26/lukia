package io.lukia.core.domain

enum class DocumentType {
    PDF,
    EPUB,
    DOCX,
    TEXT,
    MARKDOWN,
    UNKNOWN;

    companion object {
        /**
         * The single place that decides which extensions the library
         * understands. Every platform analyzer and the folder scanners share it
         * so a picked file and a scanned one are always classified the same way.
         *
         * Extensions rather than file contents, deliberately: this runs over
         * every file in an imported folder, and sniffing hundreds of them would
         * mean opening hundreds of files to decide whether to open them. What a
         * file turns out not to be is settled later, when the reader tries to
         * open it and says so.
         */
        fun fromExtension(extension: String): DocumentType =
            when (extension.lowercase()) {
                "pdf" -> PDF
                "epub" -> EPUB
                "docx" -> DOCX
                "txt", "text" -> TEXT
                "md", "markdown" -> MARKDOWN
                else -> UNKNOWN
            }
    }
}
