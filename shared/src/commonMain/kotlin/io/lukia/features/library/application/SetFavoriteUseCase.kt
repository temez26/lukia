package io.lukia.features.library.application

import io.lukia.features.library.domain.LibraryRepository

class SetFavoriteUseCase(
    private val repository: LibraryRepository,
) {
    suspend operator fun invoke(location: String, isFavorite: Boolean) {
        repository.setFavorite(location, isFavorite)
    }
}
