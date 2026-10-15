package io.lukia.api

import kotlinx.serialization.Serializable

/**
 * One page of changes, oldest first - how a device catches up on the library,
 * its book state and its reading sessions alike.
 *
 * [cursor] is what to pass as `since` next time; it only ever grows. When
 * [hasMore] is true there is another page waiting now.
 */
@Serializable
data class ChangePage<T>(
    val items: List<T>,
    val cursor: Long,
    val hasMore: Boolean,
)
