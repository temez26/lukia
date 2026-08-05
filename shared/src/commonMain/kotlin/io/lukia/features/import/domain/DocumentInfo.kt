package io.lukia.features.import.domain

data class DocumentInfo(
    val fileName: String,
    val extension: String,
    val absolutePath: String,
    val fileSizeBytes: Long
)
