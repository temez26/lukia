package io.lukia.features.reader.presentation.screens.translation

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.features.reader.domain.text.TextAnchor
import io.lukia.features.reader.presentation.translation.PassageSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * That the reader can be used by somebody who cannot point at a word.
 *
 * Everything this app is for used to be a coordinate. Translating a sentence was
 * a tap on that sentence, looking a word up was a hold on that word, and both
 * were reached through `pointerInput` and nothing else - so a reader working by
 * voice, by switch, by keyboard or through a screen reader could open a book,
 * turn its pages, and do none of the things the book was opened in this app for.
 *
 * A screen reader lands on the paragraph, which is what speaks; the action to
 * translate it is therefore on the paragraph rather than on the block around it.
 * That is the part worth a test - an action hung on a node nothing focuses is an
 * action nobody can reach, and it draws, composes and passes every other test
 * exactly as one that works.
 */
@OptIn(ExperimentalTestApi::class)
class ReaderWithoutAPointerTest {

    private fun theParagraphAction(label: String) =
        SemanticsMatcher("offers the action '$label'") { node ->
            node.config.getOrNull(SemanticsActions.CustomActions)
                .orEmpty()
                .any { it.label == label }
        }

    private val translateIt = "Translate this paragraph"

    @Test
    fun aParagraphCanBeTranslatedWithoutPointingAtIt() = runComposeUiTest {
        var selected: PassageSelection? = null

        setReader(onPassageSelected = { selected = it })

        val paragraph = onNodeWithText(opening, substring = true)
            .assert(theParagraphAction(translateIt))

        // Invoked as assistive technology invokes it, rather than by reaching
        // past the semantics tree to the callback underneath. Whether the action
        // is on a node anything can land on is the whole of what is being asked.
        paragraph.fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
            .first { it.label == translateIt }
            .action()

        waitForIdle()

        val selection = assertNotNull(selected, "the paragraph was not asked about")
        assertEquals("$opening $second", selection.passage)
        assertEquals(TextAnchor.Section(0, 0, sentenceIndex = null), selection.anchor)
    }

    /**
     * The page number is a node of its own, so a screen reader has somewhere to
     * land that says where in the book it is. It used to be a description on the
     * sheet holding the paragraphs, which reads correctly in a semantics dump
     * and is never announced by anything: a node with speaking children is
     * neither merged nor a leaf, and assistive technology stops on neither.
     */
    @Test
    fun aPageSaysWhereInTheBookItIs() = runComposeUiTest {
        setReader()

        onNodeWithText(opening, substring = true).assertExists()

        // A leaf, which is the whole point: a node with speaking children is
        // neither merged nor a leaf, and assistive technology stops on neither -
        // so the page number was in the tree and unreachable. There is more than
        // one node saying this (the toolbar says it too), and every one of them
        // has to be somewhere a reader can land.
        val labels = onAllNodes(hasContentDescription("Page 1 of ", substring = true))
            .fetchSemanticsNodes()

        assertTrue(labels.isNotEmpty(), "no page said where in the book it was")
        assertTrue(
            labels.all { it.children.isEmpty() },
            "a page number was set on a node with children, which nothing announces",
        )
    }
}
