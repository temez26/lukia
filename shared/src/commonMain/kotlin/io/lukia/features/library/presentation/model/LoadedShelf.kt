package io.lukia.features.library.presentation.model

import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.core.presentation.UiState

/**
 * What the library screen has read: the documents, and how far through each its
 * reader is - [progress] keyed by location, holding only books that have been
 * opened, for the bars the grid draws on its covers.
 *
 * One value, so that a load - which reads both - changes the shelf once; held
 * apart they were two changes, and the shelf was worked out twice.
 *
 * Still two fields rather than a position folded into each [DocumentInfo]: they
 * come from different stores, and a failure to read the positions must not take
 * the library off the screen. A grid with no bars is a small loss; a grid with
 * no books is the whole app.
 */
internal data class LoadedShelf(
    val documents: UiState<List<DocumentInfo>>,
    val progress: Map<String, ReadingProgress>,
)

/**
 * [transform] applied to the documents if they were read, and this unchanged if
 * not. A library that failed to load or is still loading has nothing to edit,
 * and inventing a Success state from a local change would put books on screen
 * that were never read.
 */
internal fun UiState<List<DocumentInfo>>.editedIfLoaded(
    transform: (List<DocumentInfo>) -> List<DocumentInfo>,
): UiState<List<DocumentInfo>> = if (this is UiState.Success) UiState.Success(transform(data)) else this
