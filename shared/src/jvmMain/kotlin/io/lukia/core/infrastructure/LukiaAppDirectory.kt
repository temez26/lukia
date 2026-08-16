package io.lukia.core.infrastructure

import java.io.File

/**
 * Per-user app storage: `%LOCALAPPDATA%\Lukia` where Windows offers it, and a
 * dotted home directory elsewhere, which is what Linux and macOS expect.
 *
 * Shared by everything the desktop build keeps between runs, so working copies
 * and preferences end up under one directory the user can find and delete.
 */
internal fun lukiaAppDirectory(): File {
    val localAppData = System.getenv("LOCALAPPDATA")

    return if (localAppData.isNullOrBlank()) {
        File(System.getProperty("user.home"), ".lukia")
    } else {
        File(localAppData, "Lukia")
    }
}
