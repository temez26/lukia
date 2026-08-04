package io.lukia.features.import.infrastructure

interface DocumentPicker {

    suspend fun pick(): PickedDocument?
}
