package io.lukia

import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * Holds the dependency directions the architecture is built on.
 *
 * None of them is something the compiler can fail: a cycle between two features, or a
 * domain package reaching up into the UI, compiles perfectly well and is visible only to
 * whoever reads the imports. Five such cycles were untangled by moving types around, and
 * nothing but this stops them coming back one convenient import at a time.
 *
 * Every rule reads the main source sets as text - the packages a file declares and the
 * imports it lists. Test sources are left out on purpose: a fixture reaching across
 * features is how a test builds its world, not a dependency the app carries.
 *
 * Where a rule is broken today the offending pair is allow-listed by name rather than the
 * rule being softened, so fixing it turns the entry stale and the test says so.
 */
class ArchitectureTest {

    /**
     * Whichever import breaks the loop is the one to delete; the entry naming that loop
     * then fails as stale, and the rule tightens on its own.
     *
     * Empty, and the intention is that it stays that way. The one loop it held -
     * library to settings to reader and back - was broken at its thinnest edge: the
     * reader wanted one method of the library's repository, so the reading half of that
     * repository became `core.domain.document.DocumentCatalogue` and the arrow went
     * away. The statistics screen's arrow went with it, for the same reason.
     */
    private val allowedCycles = emptySet<String>()

    private val layersAboveDomain = listOf("application", "infrastructure", "presentation")

    @Test
    fun noFeatureDependsOnAnotherInACircle() {
        val edges = featureEdges()
        val graph = edges.keys.groupBy({ it.first }, { it.second }).mapValues { (_, to) -> to.toSet() }
        val found = simpleCycles(graph).associate { cycle ->
            val name = (cycle + cycle.first()).joinToString(" -> ")
            name to describeCycle(name, cycle, edges)
        }

        assertAllowListed("No feature may depend on another in a circle", allowedCycles, found)
    }

    @Test
    fun coreAndSharedStayOutOfTheFeatures() {
        assertAllowListed(
            rule = "core and shared may not depend on a feature",
            allowed = emptySet(),
            found = importsWhere(
                from = { it.inPackage("io.lukia.core") || it.inPackage("io.lukia.shared") },
                to = { it.inPackage("io.lukia.features") },
            ),
        )
    }

    @Test
    fun domainStaysBelowTheLayersThatUseIt() {
        assertAllowListed(
            rule = "A domain package may not depend on application, infrastructure or presentation",
            allowed = emptySet(),
            found = importsWhere(
                from = { it.hasSegment("domain") },
                to = { import ->
                    import.inPackage("io.lukia") && layersAboveDomain.any { import.hasSegment(it) }
                },
            ),
        )
    }

    @Test
    fun infrastructureStaysOutOfPresentation() {
        assertAllowListed(
            rule = "An infrastructure package may not depend on presentation",
            allowed = emptySet(),
            found = importsWhere(
                from = { it.hasSegment("infrastructure") },
                to = { it.inPackage("io.lukia") && it.hasSegment("presentation") },
            ),
        )
    }

    @Test
    fun coreStaysOutOfShared() {
        assertAllowListed(
            rule = "core may not depend on shared",
            allowed = emptySet(),
            found = importsWhere(
                from = { it.inPackage("io.lukia.core") },
                to = { it.inPackage("io.lukia.shared") },
            ),
        )
    }
}

/**
 * Fails unless what was [found] is exactly what is [allowed] - naming the imports that
 * broke the rule, and the allow-list entries that no longer describe anything.
 *
 * [found] maps each finding's identity, which is what an allow-list entry spells, to the
 * fuller description the failure prints.
 */
private fun assertAllowListed(rule: String, allowed: Set<String>, found: Map<String, String>) {
    val violations = (found.keys - allowed).sorted()
    val stale = (allowed - found.keys).sorted()

    val report = buildList {
        if (violations.isNotEmpty()) {
            add(violations.joinToString("\n", prefix = "$rule, but:\n") { "  ${found.getValue(it)}" })
        }
        if (stale.isNotEmpty()) {
            val heading = "Allow-listed in ArchitectureTest but no longer true - delete these entries:\n"
            add(stale.joinToString("\n", prefix = heading) { "  $it" })
        }
    }
    if (report.isNotEmpty()) fail(report.joinToString("\n\n"))
}

/** Every import from a package matching [from] to one matching [to], keyed by itself. */
private fun importsWhere(from: (String) -> Boolean, to: (String) -> Boolean): Map<String, String> =
    mainSources
        .filter { from(it.packageName) }
        .flatMap { source -> source.imports.filter(to).map { "${source.path} imports $it" } }
        .associateWith { it }

/** Which feature imports which, with the imports that put each arrow there. */
private fun featureEdges(): Map<Pair<String, String>, List<String>> =
    mainSources
        .mapNotNull { source -> featureOf(source.packageName)?.let { it to source } }
        .flatMap { (from, source) ->
            source.imports.mapNotNull { import ->
                featureOf(import)
                    ?.takeIf { it != from }
                    ?.let { to -> (from to to) to "${source.path} imports $import" }
            }
        }
        .groupBy({ it.first }, { it.second })

/**
 * Every simple cycle in [graph], each found once and starting from its alphabetically
 * first feature, so the same loop always gets the same name to allow-list.
 */
private fun simpleCycles(graph: Map<String, Set<String>>): List<List<String>> {
    val cycles = mutableListOf<List<String>>()

    fun extend(start: String, path: List<String>) {
        graph[path.last()].orEmpty().forEach { next ->
            when {
                next == start -> cycles += path
                next > start && next !in path -> extend(start, path + next)
                else -> Unit
            }
        }
    }

    graph.keys.forEach { extend(it, listOf(it)) }
    return cycles
}

private fun describeCycle(name: String, cycle: List<String>, edges: Map<Pair<String, String>, List<String>>): String =
    (cycle + cycle.first())
        .zipWithNext()
        .joinToString("\n", prefix = "$name\n") { edge ->
            val imports = edges.getValue(edge)
            "    ${edge.first} -> ${edge.second}, ${imports.size} of them, one being ${imports.first()}"
        }

private class KotlinSource(
    /** Relative to the repository root, so a failure names a path that can be opened. */
    val path: String,
    val packageName: String,
    val imports: List<String>,
)

private val featurePattern = Regex("""^io\.lukia\.features\.(\w+)""")

private fun featureOf(fullyQualifiedName: String): String? =
    featurePattern.find(fullyQualifiedName)?.groupValues?.get(1)

private fun String.inPackage(prefix: String) = this == prefix || startsWith("$prefix.")

private fun String.hasSegment(segment: String) = segment in split('.')

private const val SOURCE_ROOT = "shared/src"

private val repositoryRoot: File =
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .firstOrNull { File(it, SOURCE_ROOT).isDirectory }
        ?: error("No $SOURCE_ROOT above ${System.getProperty("user.dir")} - the test cannot find the sources.")

/**
 * Reads the two declarations the rules are about. Anchoring at the start of the line is
 * enough to tell them from the word appearing inside code: neither keyword is ever
 * indented, and ktlint forbids the wildcard form.
 */
private val declarationPattern = Regex("""^(package|import)\s+([\w.]+)""")

private fun File.asKotlinSource(): KotlinSource {
    var packageName = ""
    val imports = mutableListOf<String>()
    useLines { lines ->
        lines.forEach { line ->
            val (keyword, name) = (declarationPattern.find(line) ?: return@forEach).destructured
            if (keyword == "package") packageName = name else imports += name
        }
    }
    return KotlinSource(relativeTo(repositoryRoot).invariantSeparatorsPath, packageName, imports)
}

// Declared last because a top-level property may only read the ones above it.
private val mainSources: List<KotlinSource> = File(repositoryRoot, SOURCE_ROOT)
    .listFiles()
    .orEmpty()
    .filter { it.isDirectory && it.name.endsWith("Main") }
    .flatMap { sourceSet -> sourceSet.walkTopDown().filter { it.isFile && it.extension == "kt" } }
    .map { it.asKotlinSource() }
    .also {
        check(it.isNotEmpty()) {
            "No Kotlin sources under $repositoryRoot/$SOURCE_ROOT - the scan is broken, not the architecture."
        }
    }
