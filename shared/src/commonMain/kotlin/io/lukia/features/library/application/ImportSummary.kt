package io.lukia.features.library.application

/**
 * What became of a batch of files handed to the import.
 *
 * The three outcomes are kept apart because they mean different things to the
 * reader: [imported] is what arrived, [alreadyInLibrary] is what was there all
 * along, and [failed] is the only one worth being sorry about.
 */
data class ImportSummary(
    val imported: Int,
    val alreadyInLibrary: Int,
    val failed: Int,
)
