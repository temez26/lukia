package io.lukia.server.http

import io.ktor.server.application.ApplicationCall
import io.lukia.api.ChangePage

/**
 * A page of the rows [fetch] returns after [since], ordered by their `seq`.
 *
 * Asks for one row more than [limit], which says whether another page follows
 * without counting the rest.
 */
fun <R, T> changePage(
    since: Long,
    limit: Int,
    fetch: (since: Long, limit: Long) -> List<R>,
    seqOf: (R) -> Long,
    toItem: (R) -> T,
): ChangePage<T> {
    val rows = fetch(since, limit + 1L)
    val page = rows.take(limit)

    return ChangePage(
        items = page.map(toItem),
        cursor = page.lastOrNull()?.let(seqOf) ?: since,
        hasMore = rows.size > limit,
    )
}

/** The `since` a device asked from: zero, meaning everything, when it is missing or makes no sense. */
val ApplicationCall.since: Long
    get() = request.queryParameters["since"]?.toLongOrNull()?.coerceAtLeast(0) ?: 0

/** How many changes one page carries: small enough to answer quickly, large enough that catching up is few requests. */
const val ChangePageSize = 500
