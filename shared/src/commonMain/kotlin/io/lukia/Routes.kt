package io.lukia

import kotlin.io.encoding.Base64

sealed class Routes(val route: String) {

    data object Library : Routes("library")

    /**
     * A destination that shows one book, addressed by its library location.
     *
     * A location is an absolute path, a content:// URI or a file:// URL, none of
     * which can be dropped into a route as they are - the separators in them
     * read as route structure. Encoding turns the whole thing into one opaque
     * path segment.
     */
    sealed class DocumentRoute(private val prefix: String) :
        Routes("$prefix/{$ARG_LOCATION_TOKEN}") {

        fun createRoute(location: String): String =
            "$prefix/${Base64.UrlSafe.encode(location.encodeToByteArray())}"

        fun decodeLocation(locationToken: String): String =
            Base64.UrlSafe.decode(locationToken).decodeToString()

        companion object {
            const val ARG_LOCATION_TOKEN = "locationToken"
        }
    }

    data object Detail : DocumentRoute("detail")

    data object Reader : DocumentRoute("reader")
}
