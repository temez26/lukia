package io.lukia.features.profile.infrastructure

import io.lukia.core.infrastructure.db.LukiaDatabase
import io.lukia.features.profile.domain.ProfilePicture
import io.lukia.features.profile.domain.ProfilePictureStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class SqlProfilePictureStore(
    database: LukiaDatabase,
    /** Injectable so tests can run against a database deterministically. */
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ProfilePictureStore {

    private val pictures = database.profilePictureQueries

    override suspend fun picture(): ProfilePicture? = withContext(dispatcher) {
        pictures.select().executeAsOneOrNull()?.let(::ProfilePicture)
    }

    override suspend fun save(picture: ProfilePicture) = withContext(dispatcher) {
        pictures.put(picture.bytes)
        Unit
    }

    override suspend fun clear() = withContext(dispatcher) {
        pictures.delete()
        Unit
    }
}
