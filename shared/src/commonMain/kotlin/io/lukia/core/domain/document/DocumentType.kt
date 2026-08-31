package io.lukia.core.domain.document

enum class DocumentType {
    PDF,
    EPUB,
    DOCX,
    TEXT,
    MARKDOWN,
    UNKNOWN,
    ;

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
            byExtension[extension.lowercase()] ?: UNKNOWN

        /**
         * The same extensions, for a caller that has to list them rather than
         * test one - a file dialog's filter, most of all.
         *
         * Read off the same table [fromExtension] answers from, because the
         * alternative is a second list: the desktop picker kept one of its own
         * until this existed, and it had drifted to offering `.text` files the
         * library would have taken and none of the `.markdown` ones it would.
         */
        val supportedExtensions: Set<String> get() = byExtension.keys

        private val byExtension = mapOf(
            "pdf" to PDF,
            "epub" to EPUB,
            "docx" to DOCX,
            "txt" to TEXT,
            "text" to TEXT,
            "md" to MARKDOWN,
            "markdown" to MARKDOWN,
        )
    }
}
