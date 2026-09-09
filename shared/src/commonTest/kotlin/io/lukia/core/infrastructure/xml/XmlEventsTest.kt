package io.lukia.core.infrastructure.xml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class XmlEventsTest {

    @Test
    fun readsElementsAndTheTextBetweenThem() {
        val events = xmlEvents("<p>Call me <em>Ishmael</em>.</p>").toList()

        assertEquals(
            listOf("p", "em"),
            events.filterIsInstance<XmlEvent.Open>().map { it.name },
        )
        assertEquals(
            listOf("Call me ", "Ishmael", "."),
            events.filterIsInstance<XmlEvent.Text>().map { it.text },
        )
        assertEquals(
            listOf("em", "p"),
            events.filterIsInstance<XmlEvent.Close>().map { it.name },
        )
    }

    /** A prefix says which vocabulary an element is from, not which element. */
    @Test
    fun namespacePrefixesAreDroppedFromElementNames() {
        val names = xmlEvents("<w:p><w:r><w:t>text</w:t></w:r></w:p>")
            .filterIsInstance<XmlEvent.Open>()
            .map { it.name }
            .toList()

        assertEquals(listOf("p", "r", "t"), names)
    }

    /**
     * Attribute prefixes are kept, because they are the whole meaning: `epub:type`
     * is what marks a navigation list and `w:val` what a Word toggle is set to.
     */
    @Test
    fun attributePrefixesAreKept() {
        val open = xmlEvents("""<nav epub:type="toc" ID="x"><a HREF='c1.xhtml'>""")
            .filterIsInstance<XmlEvent.Open>()
            .toList()

        assertEquals("toc", open[0].attributes["epub:type"])
        assertEquals("x", open[0].attributes["id"])
        assertEquals("c1.xhtml", open[1].attributes["href"])
    }

    @Test
    fun selfClosingElementsAreMarkedAsSuch() {
        val open = xmlEvents("""<img src="a.png"/><p>""")
            .filterIsInstance<XmlEvent.Open>()
            .toList()

        assertTrue(open[0].selfClosing)
        assertEquals("a.png", open[0].attributes["src"])
        assertTrue(!open[1].selfClosing)
    }

    @Test
    fun decodesNamedAndNumericEntities() {
        val text = xmlEvents("<p>caf&eacute; &amp; &#8212; &#x201C;quoted&#x201D; &nope;</p>")
            .filterIsInstance<XmlEvent.Text>()
            .joinToString(separator = "") { it.text }

        assertEquals("café & — “quoted” &nope;", text)
    }

    @Test
    fun skipsCommentsDoctypesAndCdata() {
        val source = """
            <!DOCTYPE html>
            <!-- a note with <tags> in it -->
            <p>kept</p>
            <![CDATA[ .cover { display: none } ]]>
        """.trimIndent()

        val text = xmlEvents(source)
            .filterIsInstance<XmlEvent.Text>()
            .joinToString(separator = "") { it.text }
            .trim()

        assertEquals("kept", text)
    }

    @Test
    fun anAngleBracketInsideAnAttributeDoesNotEndTheTag() {
        val open = xmlEvents("""<img alt="a > b" src="x.png"/>""")
            .filterIsInstance<XmlEvent.Open>()
            .first()

        assertEquals("a > b", open.attributes["alt"])
        assertEquals("x.png", open.attributes["src"])
    }

    /**
     * An unescaped `<` is something a producer failed to escape rather than
     * something the reader should choke on, so it survives as the text it is.
     */
    @Test
    fun anUnescapedAngleBracketIsTreatedAsText() {
        val text = xmlEvents("<p>5 < 6 and 7 > 6</p>")
            .filterIsInstance<XmlEvent.Text>()
            .joinToString(separator = "") { it.text }

        assertEquals("5 ", text.take(2))
        assertTrue("6 and 7 > 6" in text)
    }

    @Test
    fun anUnfinishedTagAtTheEndOfTheFileDoesNotHangOrThrow() {
        val events = xmlEvents("<p>text</p><div class=\"unfinished").toList()

        assertEquals("text", events.filterIsInstance<XmlEvent.Text>().first().text)
    }
}
