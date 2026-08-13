package io.lukia

import kotlin.io.encoding.Base64

sealed class Routes(val route: String) {

    data object Home : Routes("home")
    data object Reader : Routes("reader")

    data object Detail : Routes("detail/{locationToken}") {
        const val ARG_LOCATION_TOKEN = "locationToken"

        fun createRoute(location: String): String {
            return "detail/${Base64.UrlSafe.encode(location.encodeToByteArray())}"
        }

        fun decodeLocation(locationToken: String): String {
            return Base64.UrlSafe.decode(locationToken).decodeToString()
        }
    }
}