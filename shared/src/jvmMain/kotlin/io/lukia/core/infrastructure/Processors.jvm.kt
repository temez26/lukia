package io.lukia.core.infrastructure

internal actual fun availableProcessors(): Int = Runtime.getRuntime().availableProcessors()
