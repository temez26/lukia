package io.lukia.features.library.domain

import io.lukia.core.domain.PickedDocument
import io.lukia.core.domain.PickedFolder

/**
 * How many levels below the chosen folder a scan will look.
 *
 * A cap rather than an unbounded walk: a desktop folder may contain a symlink
 * that points back at one of its own parents, and a scan that followed it would
 * never finish. Deep enough that a shelf organised by author, then series, then
 * book still comes in whole.
 */
const val MaxFolderScanDepth = 8

interface FolderScanner {
    /**
     * Returns every supported document inside [folder], including the ones in
     * its sub-folders down to [MaxFolderScanDepth].
     *
     * Sub-folders are included because a library on disk is almost never flat -
     * it is organised, and an import that only saw the top level would bring in
     * whichever few books happened to be lying loose in it.
     */
    suspend fun scan(folder: PickedFolder): List<PickedDocument>
}
