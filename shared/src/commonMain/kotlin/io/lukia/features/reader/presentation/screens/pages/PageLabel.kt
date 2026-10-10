package io.lukia.features.reader.presentation.screens.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex

/**
 * Where in the book a page is, for anything that cannot see the number in the
 * toolbar.
 *
 * A node of its own rather than a description on the page. Both kinds of page
 * carry something inside them that speaks - a reflowable one is a column of
 * paragraphs, a fixed one grows a translation panel - and a description set on a
 * container whose children speak is never heard: the container is neither a
 * merged node nor a leaf, so a screen reader stops on neither it nor the number.
 * Merging the page would fix that by making it one node, which is exactly what
 * this reader must not do - the point of setting a book as blocks is that a
 * reader moves through it a paragraph at a time.
 *
 * So the number is a leaf. It covers the whole page, so it is what a screen
 * reader lands on when it reaches the page at all, and it draws nothing and
 * takes no pointer input - a press goes straight through. Callers put it inside
 * a traversal group, which makes the index below an order within the page.
 */
@Composable
internal fun PageLabel(spoken: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                traversalIndex = -1f
                contentDescription = spoken
            },
    )
}
