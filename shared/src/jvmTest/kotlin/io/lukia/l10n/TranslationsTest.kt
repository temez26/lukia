package io.lukia.l10n

import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.fail

/**
 * Holds every translation against the file the app was written in.
 *
 * A missing key or a mistyped argument is not something the compiler can see -
 * `Res.string.x` resolves against the default file, and a translation is data
 * read at run time. The first anyone would know is a screen showing English in
 * the middle of Finnish, or a crash where a format was handed the wrong number
 * of arguments.
 *
 * Reads the XML as the app's own resources rather than through `Res`, because
 * what is being checked is the pair of files rather than either one of them.
 */
class TranslationsTest {

    @Test
    fun everyTranslationCarriesEveryKey() {
        val missing = translations().flatMap { (locale, translated) ->
            (defaultStrings.keys - translated.keys).map { "$locale is missing $it" }
        }

        if (missing.isNotEmpty()) {
            fail("A translation has fallen behind:\n" + missing.sorted().joinToString("\n"))
        }
    }

    /**
     * A key nothing reads is a key somebody will translate for nothing, and is
     * usually the last trace of a string the default file has already dropped.
     */
    @Test
    fun noTranslationInventsAKey() {
        val extra = translations().flatMap { (locale, translated) ->
            (translated.keys - defaultStrings.keys).map { "$locale has $it, which nothing declares" }
        }

        if (extra.isNotEmpty()) {
            fail("A translation names something the app does not:\n" + extra.sorted().joinToString("\n"))
        }
    }

    /**
     * The one that would crash rather than read oddly.
     *
     * A translation may leave an argument out - a plural's singular form often
     * has no use for the count it is chosen by - but it may never ask for one
     * the call site does not supply.
     */
    @Test
    fun noTranslationAsksForAnArgumentTheAppDoesNotSupply() {
        val wrong = translations().flatMap { (locale, translated) ->
            translated.entries.mapNotNull { (key, forms) ->
                val supplied = defaultStrings[key]?.values?.flatMap(::argumentsIn)?.toSet().orEmpty()
                val asked = forms.values.flatMap(::argumentsIn).toSet()

                (asked - supplied).takeIf { it.isNotEmpty() }?.let {
                    "$locale: $key asks for ${it.sorted()}, and the app supplies ${supplied.sorted()}"
                }
            }
        }

        if (wrong.isNotEmpty()) {
            fail("A translation would be handed the wrong arguments:\n" + wrong.sorted().joinToString("\n"))
        }
    }
}

/**
 * One file's entries: a key, and the forms under it. A plain string has the one
 * form under the empty quantity; a plural has one per quantity, which is what
 * lets the two be compared without either pretending to be the other.
 */
private typealias Entries = Map<String, Map<String, String>>

/** The child elements, which is all this cares about among the whitespace nodes. */
private fun NodeList.elements(): List<Element> =
    (0 until length).mapNotNull { item(it) as? Element }

/**
 * A plain string's one form, or a plural's form per quantity - and null for
 * anything else the file might hold, which is nothing today.
 */
private fun Element.formsOrNull(): Map<String, String>? = when (tagName) {
    "string" -> mapOf("" to textContent)
    "plurals" -> childNodes.elements().associate { it.getAttribute("quantity") to it.textContent }
    else -> null
}

/** Each `%1$d` or `%2$s` in a string, as its position and its type together. */
private fun argumentsIn(text: String): List<String> =
    Regex("""%(\d+)\$([a-z])""").findAll(text).map { it.value }.toList()

private const val RESOURCE_ROOT = "shared/src/commonMain/composeResources"

private val repositoryRoot: File =
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .firstOrNull { File(it, RESOURCE_ROOT).isDirectory }
        ?: error("No $RESOURCE_ROOT above ${System.getProperty("user.dir")}.")

private fun File.asEntries(): Entries {
    val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(this)
    val entries = mutableMapOf<String, Map<String, String>>()

    document.documentElement.childNodes.elements().forEach { element ->
        val forms = element.formsOrNull() ?: return@forEach

        entries[element.getAttribute("name")] = forms
    }

    return entries
}

private val defaultStrings: Entries =
    File(repositoryRoot, "$RESOURCE_ROOT/values/strings.xml").asEntries()

/** Every `values-xx` beside the default one, by the locale its directory names. */
private fun translations(): List<Pair<String, Entries>> =
    File(repositoryRoot, RESOURCE_ROOT)
        .listFiles()
        .orEmpty()
        .filter { it.isDirectory && it.name.startsWith("values-") }
        .sortedBy { it.name }
        .map { it.name.removePrefix("values-") to File(it, "strings.xml").asEntries() }
