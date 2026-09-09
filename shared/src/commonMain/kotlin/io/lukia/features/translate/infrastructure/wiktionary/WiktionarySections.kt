package io.lukia.features.translate.infrastructure.wiktionary

/**
 * A Wiktionary page cut into the sections its headings mark out.
 *
 * The query API hands a page over as one run of text with its headings still
 * wrapped in `=` signs, and everything downstream asks which part of the page a
 * line came from: senses, related words, or a section about the word that never
 * says what it means.
 *
 * [level] is how deep the heading is - `== Englanti ==` is 2, `=== Verbi ===` is
 * 3 - the only thing saying whether a section is inside another or beside it,
 * which matters because related terms sit under a part of speech while the next
 * language sits beside it.
 */
internal data class WikiSection(
    val title: String,
    val level: Int,
    val lines: List<String>,
) {

    /**
     * What this section is, judged by its heading alone.
     *
     * Deliberately still not an attempt to find the right language's section.
     * Which language the word belongs to is what the reader is looking it up to
     * find out, and the headings naming it are written in the wiki's own
     * language rather than in a code this could match.
     */
    val kind: Kind
        get() {
            val name = title.lowercase()

            return when {
                RelatedHeadings.any { name.startsWith(it) } -> Kind.Related
                AsideHeadings.any { name.startsWith(it) } -> Kind.Aside
                else -> Kind.Senses
            }
        }

    enum class Kind {

        /** A part of speech, under which a page lists what the word means. */
        Senses,

        /** Other words the entry points at - derived terms, compounds. */
        Related,

        /**
         * A section about the word that never says what it means.
         *
         * Named by what is in it rather than left to the sense rules, because
         * those rules cannot tell `IPA(key): /skɪlz/` from a definition. Read as
         * senses it is what an English fallback puts over a word in the book,
         * which is how a reader ends up with a pronunciation where a translation
         * should be.
         */
        Aside,
    }
}

/**
 * The page's sections in the order it wrote them, starting with whatever stood
 * above the first heading.
 *
 * That first section is why this returns a list rather than a map: a stub page
 * can be a line of text with no headings at all, and dropping it would turn the
 * only entry a wiki had into no entry.
 */
internal fun String.sections(): List<WikiSection> {
    val sections = mutableListOf<WikiSection>()
    var title = ""
    var level = 0
    var lines = mutableListOf<String>()

    for (raw in lineSequence()) {
        val line = raw.trim()

        if (line.startsWith("=")) {
            sections += WikiSection(title, level, lines)
            title = line.trim('=', ' ').trim()
            level = line.takeWhile { it == '=' }.length
            lines = mutableListOf()
        } else if (line.isNotEmpty()) {
            lines += line
        }
    }

    sections += WikiSection(title, level, lines)

    return sections
}

/** Headings that list other words rather than meanings. */
private val RelatedHeadings = setOf(
    "liittyvät sanat",
    "sanaliitot",
    "johdokset",
    "yhdyssanat",
    "synonyymit",
    "derived terms",
    "related terms",
    "coordinate terms",
    "synonyms",
    "compounds",
)

/**
 * Headings that are about the word without defining it.
 *
 * Matched on the front of the heading because the English Wiktionary numbers
 * some of them - `Etymology 1`, `Etymology 2` - and a word with two origins
 * would otherwise have its first one read out as a meaning.
 */
private val AsideHeadings = setOf(
    "ääntäminen",
    "etymologia",
    "käännökset",
    "taivutus",
    "lähteet",
    "viitteet",
    "katso myös",
    "anagrammit",
    "pronunciation",
    "hyphenation",
    "etymology",
    "translations",
    "declension",
    "conjugation",
    "inflection",
    "alternative forms",
    "usage notes",
    "quotations",
    "descendants",
    "antonyms",
    "references",
    "further reading",
    "see also",
    "anagrams",
)
