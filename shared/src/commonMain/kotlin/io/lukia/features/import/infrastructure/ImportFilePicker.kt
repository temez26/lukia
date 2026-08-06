package io.lukia.features.import.infrastructure

import io.lukia.features.import.domain.PickedDocument

interface ImportFilePicker {
    suspend fun pick(): PickedDocument?
}
