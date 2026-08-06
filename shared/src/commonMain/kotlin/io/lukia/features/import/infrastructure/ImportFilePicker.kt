package io.lukia.features.import.presentation

import io.lukia.features.import.domain.PickedDocument

interface ImportFilePicker {
    suspend fun pick(): PickedDocument?
}
