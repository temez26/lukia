package io.lukia.core.domain

/**
 * A folder the user selected as a library path. [location] is a
 * platform-specific handle (absolute path on desktop, a tree URI on Android),
 * so it is only ever consumed by the platform folder scanner that produced it.
 */
data class PickedFolder(
    val name: String,
    val location: String,
)
