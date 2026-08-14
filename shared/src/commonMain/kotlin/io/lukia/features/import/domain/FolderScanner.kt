package io.lukia.features.import.domain

import io.lukia.core.domain.PickedDocument
import io.lukia.core.domain.PickedFolder

interface FolderScanner {
    /** Returns every supported document directly inside [folder]. */
    suspend fun scan(folder: PickedFolder): List<PickedDocument>
}
