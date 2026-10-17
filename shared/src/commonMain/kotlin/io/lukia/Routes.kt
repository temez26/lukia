package io.lukia

import kotlin.io.encoding.Base64

sealed class Routes(val route: String) {

    /**
     * The three destinations the mobile tab bar switches between. They are
     * ordinary destinations rather than a nested graph: what makes them tabs is
     * that the bar navigates between them and no screen pushes onto them.
     *
     * [Library] is also the desktop build's only starting point, where the tab
     * bar does not exist at all.
     */
    data object Home : Routes("home")

    data object Search : Routes("search")

    data object Library : Routes("library")

    /**
     * All of one home row, pushed over the tabs. Addressed by the row's name -
     * `HomeShelf.name` - so this file needs nothing from the library feature.
     */
    data object HomeShelf : Routes("shelf/{$ARG_SHELF}") {
        fun createRoute(shelf: String): String = "shelf/$shelf"
    }

    data object Settings : Routes("settings")

    /** Settings opened on the Account page, where syncing is reported and signed in to. */
    data object AccountSettings : Routes("settings/account")

    /**
     * What the reading adds up to. A destination pushed over whatever was
     * showing, like [Settings] and for the same reason: it is about the app
     * rather than about one book, and it is reached from the same bar.
     */
    data object Stats : Routes("stats")

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

    companion object {
        const val ARG_SHELF = "shelf"
    }
}
