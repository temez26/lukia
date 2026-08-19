package io.lukia

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
