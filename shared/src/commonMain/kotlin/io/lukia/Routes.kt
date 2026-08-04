package io.lukia

sealed class Routes(val route: String) {

    data object Home : Routes("home")
    data object Library : Routes("library")
    data object Reader : Routes("reader")
}