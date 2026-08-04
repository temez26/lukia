package io.lukia.features.import.infrastructure

interface DocumentStorage {

    suspend fun store(
        document: PickedDocument
    ): StoredDocument
}