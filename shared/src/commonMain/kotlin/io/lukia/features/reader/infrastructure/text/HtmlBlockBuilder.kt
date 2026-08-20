package io.lukia.features.reader.infrastructure.text

import io.lukia.core.infrastructure.xml.XmlEvent
import io.lukia.features.reader.domain.Emphasis
import io.lukia.features.reader.domain.TextBlock
import io.lukia.features.reader.domain.TextRun

/** What the innermost open element makes the text inside it. */
private enum class BlockKind {
    Paragraph,
    Heading,
    ListItem,
    Quote,
    Code,
}

private class OpenBlock(
    val tag: String,
    val kind: BlockKind,
    /** Only meaningful for [BlockKind.Heading]. */
    val headingLevel: Int = 0,
    /** Only meaningful for [BlockKind.ListItem]. */
    val marker: String = "",
)

private class ListContext(val ordered: Boolean) {
    var nextNumber: Int = 1
}

/**
 * Nothing inside these is prose, and showing what is would mean reading a
 * stylesheet or a script aloud to someone who asked for a book.
 */
private val IgnoredElements = setOf("script", "style", "head")

private val EmphasisElements = mapOf(
    "b" to Emphasis.Bold,
    "strong" to Emphasis.Bold,
    "i" to Emphasis.Italic,
    "em" to Emphasis.Italic,
    "cite" to Emphasis.Italic,
    "dfn" to Emphasis.Italic,
    "var" to Emphasis.Italic,
    "code" to Emphasis.Monospace,
    "kbd" to Emphasis.Monospace,
    "samp" to Emphasis.Monospace,
    "tt" to Emphasis.Monospace,
)

/** Elements that end whatever text came before them and start something new. */
private val BlockElements = setOf(
    "p", "div", "section", "article", "aside", "header", "footer", "main",
    "blockquote", "pre", "li", "dt", "dd", "figcaption", "caption",
    "h1", "h2", "h3", "h4", "h5", "h6", "td", "th", "tr",
)

/** Elements with no content and so no closing tag to wait for. */
private val VoidElements = setOf("img", "image", "br", "hr", "meta", "link", "col")

/**
 * Walks the event stream once, keeping only as much state as the shape of the
 * text depends on: what is open, what is emphasised, and how deep into lists it
 * is.
 */
internal class HtmlBlockBuilder(private val image: (src: String) -> ByteArray?) {

    private val blocks = mutableListOf<TextBlock>()
    private val runs = mutableListOf<TextRun>()

    private val openBlocks = mutableListOf<OpenBlock>()
    private val emphasis = mutableListOf<Emphasis>()
    private val lists = mutableListOf<ListContext>()

    private val anchors = mutableMapOf<String, Int>()

    /**
     * Ids seen since the last block was emitted. An id names a place rather than
     * a thing - it is written on the element that opens a chapter, not on its
     * first paragraph - so it belongs to whatever block comes next.
     */
    private val pendingAnchors = mutableSetOf<String>()

    /** Depth inside an ignored element, since they nest. */
    private var ignoreDepth = 0

    /** Depth inside `pre`, where whitespace is content rather than layout. */
    private var preformattedDepth = 0

    /** Raw text of the innermost `pre`, gathered without collapsing. */
    private val preformatted = StringBuilder()

    fun accept(event: XmlEvent) {
        when (event) {
            is XmlEvent.Open -> open(event)
            is XmlEvent.Close -> close(event.name)
            is XmlEvent.Text -> text(event.text)
        }
    }

    fun finish(): HtmlContent {
        flush()

        // An id that no block followed marks the end of the file, which is the
        // last block there is rather than nowhere at all.
        if (blocks.isNotEmpty()) pendingAnchors.forEach { anchors[it] = blocks.lastIndex }

        return HtmlContent(blocks = blocks.toList(), anchors = anchors.toMap())
    }

    private fun open(event: XmlEvent.Open) {
        val name = event.name

        if (name in IgnoredElements) {
            // Self-closing, so there is no close to balance and nothing inside.
            if (!event.selfClosing) ignoreDepth++
            return
        }

        if (ignoreDepth > 0) return

        event.attributes["id"]?.takeIf { it.isNotEmpty() }?.let(pendingAnchors::add)

        if (name in VoidElements) {
            void(name, event.attributes)
            return
        }

        EmphasisElements[name]?.let {
            if (!event.selfClosing) emphasis += it
            return
        }

        when (name) {
            "ul", "ol" -> {
                flush()
                lists += ListContext(ordered = name == "ol")
                return
            }

            "pre" -> {
                flush()
                preformattedDepth++
                openBlocks += OpenBlock(tag = name, kind = BlockKind.Code)
                return
            }
        }

        if (name !in BlockElements) return

        flush()
        openBlocks += blockFor(name)
    }

    private fun blockFor(name: String): OpenBlock = when {
        name.length == 2 && name[0] == 'h' && name[1] in '1'..'6' ->
            OpenBlock(
                tag = name,
                kind = BlockKind.Heading,
                headingLevel = name[1].digitToInt(),
            )

        name == "blockquote" -> OpenBlock(tag = name, kind = BlockKind.Quote)

        name == "li" -> OpenBlock(tag = name, kind = BlockKind.ListItem, marker = nextMarker())

        else -> OpenBlock(tag = name, kind = BlockKind.Paragraph)
    }

    /**
     * The bullet or number this item shows. Numbered here rather than when the
     * list is drawn, because by then the items are separate blocks that no
     * longer know what list they came from - and a document can nest one list
     * inside another.
     */
    private fun nextMarker(): String {
        val list = lists.lastOrNull() ?: return "•"

        if (!list.ordered) return "•"

        return "${list.nextNumber++}."
    }

    private fun void(name: String, attributes: Map<String, String>) {
        when (name) {
            "hr" -> {
                flush()
                emit(TextBlock.Separator)
            }

            "br" -> if (runs.isNotEmpty()) runs += TextRun("\n")

            // `image` with `xlink:href` is how an SVG-wrapped cover is written,
            // which is what a great many EPUBs use for their first page.
            "img", "image" -> {
                val source = attributes["src"] ?: attributes["xlink:href"] ?: attributes["href"]
                val bytes = source?.let(image) ?: return

                flush()
                emit(TextBlock.Figure(bytes = bytes, description = attributes["alt"]))
            }
        }
    }

    private fun close(name: String) {
        if (name in IgnoredElements) {
            if (ignoreDepth > 0) ignoreDepth--
            return
        }

        if (ignoreDepth > 0) return

        EmphasisElements[name]?.let { closing ->
            // Removes the innermost matching mark rather than the last one
            // pushed, so `<b><i>…</b></i>` - which producers do write - closes
            // the mark it names instead of corrupting everything after it.
            val index = emphasis.lastIndexOf(closing)
            if (index >= 0) emphasis.removeAt(index)
            return
        }

        if (name == "ul" || name == "ol") {
            flush()
            lists.removeLastOrNull()
            return
        }

        if (name == "pre") {
            flush()
            if (preformattedDepth > 0) preformattedDepth--
        }

        if (name !in BlockElements && name != "pre") return

        flush()

        // Closes the innermost matching element and everything a document left
        // open inside it - an unclosed `<p>` is otherwise an element that stays
        // open for the rest of the chapter.
        val index = openBlocks.indexOfLast { it.tag == name }
        if (index >= 0) {
            while (openBlocks.size > index) openBlocks.removeAt(openBlocks.lastIndex)
        }
    }

    private fun text(text: String) {
        if (ignoreDepth > 0) return

        if (preformattedDepth > 0) {
            preformatted.append(text)
            return
        }

        val collapsed = text.collapseWhitespace()

        // Whitespace between two block elements is layout in the source file,
        // not a space in the sentence - there is no sentence yet to put it in.
        if (collapsed.isBlank() && runs.isEmpty()) return

        runs.append(collapsed, emphasis.toSet())
    }

    /** Emits whatever text has gathered as the block the open elements make it. */
    private fun flush() {
        if (preformattedDepth > 0 || preformatted.isNotEmpty()) {
            val code = preformatted.toString().trim('\n')
            preformatted.clear()

            if (code.isNotBlank()) emit(TextBlock.Code(code))
            if (preformattedDepth > 0) return
        }

        val text = runs.trimmedEnds()
        runs.clear()

        if (text.isEmpty()) return

        val block = openBlocks.lastOrNull()

        emit(
            when {
                block == null -> TextBlock.Paragraph(text)

                block.kind == BlockKind.Heading ->
                    TextBlock.Heading(text = text, level = block.headingLevel)

                block.kind == BlockKind.ListItem ->
                    TextBlock.ListItem(text = text, marker = block.marker)

                // A quotation's own paragraphs are pushed as paragraphs, so what
                // makes them quoted is the blockquote further out rather than the
                // element the text is directly inside.
                block.kind == BlockKind.Quote || openBlocks.any { it.kind == BlockKind.Quote } ->
                    TextBlock.Quote(text)

                else -> TextBlock.Paragraph(text)
            },
        )
    }

    /** Adds a block, and gives it every id that has been waiting for one. */
    private fun emit(block: TextBlock) {
        pendingAnchors.forEach { anchors[it] = blocks.size }
        pendingAnchors.clear()

        blocks += block
    }
}
