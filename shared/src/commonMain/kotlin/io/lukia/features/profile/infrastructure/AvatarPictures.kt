package io.lukia.features.profile.infrastructure

import io.lukia.core.infrastructure.shrinkCoverImage
import io.lukia.features.profile.domain.ProfilePicture

/**
 * How large the reader's picture is kept, along its longer edge.
 *
 * Small, because of how small it is ever drawn: the largest the disc gets is on
 * the profile page, and even there it is under a hundred points across. Storing
 * a twelve-megapixel photograph to draw a circle the size of a thumbnail would
 * cost a megabyte of database and a decode on every screen that shows the
 * reader. Twice the drawn size covers the densest screens this app runs on.
 */
private const val MaxAvatarEdge = 256

/**
 * The most a stored picture may weigh.
 *
 * The same reasoning as `MaxStoredCoverBytes`, an order of magnitude smaller
 * because this is an order of magnitude smaller a picture. What it catches is
 * not an ordinary photograph - one shrunk to [MaxAvatarEdge] lands well under
 * this - but a file no platform decoder recognised, which comes back from
 * shrinking unchanged and could be anything at all.
 */
private const val MaxStoredAvatarBytes = 200_000

/**
 * What the reader picked, as the app will keep it - or null when it is not an
 * image this device can make sense of.
 *
 * Shrinking is borrowed from the import's cover handling rather than written
 * again: it is the same question asked of the same platform decoders, and a
 * second implementation of it would be a second set of bugs on three platforms.
 * It answers null when the image already fits, which is why the original is kept
 * in that case rather than treated as a failure.
 */
internal fun storedAvatar(bytes: ByteArray): ProfilePicture? {
    if (bytes.isEmpty()) return null

    val shrunk = runCatching { shrinkCoverImage(bytes, MaxAvatarEdge) }.getOrNull() ?: bytes

    return shrunk.takeIf { it.size <= MaxStoredAvatarBytes }?.let(::ProfilePicture)
}
