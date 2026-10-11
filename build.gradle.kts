import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import io.gitlab.arturbosch.detekt.report.ReportMergeTask
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.androidTest) apply false
    alias(libs.plugins.baselineProfile) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.kover) apply false
}

/**
 * Fails the build for Kotlin files longer than [maxLines], warning from [warnLines] on.
 *
 * detekt reasons about classes and functions, not files, and a Compose screen is a file
 * full of top-level functions - so no built-in rule covers the limit the project cares
 * about most. Paths listed in the baseline are reported as warnings instead of failures,
 * so files that predate the rule do not block unrelated work.
 */
abstract class CheckKotlinFileLength : DefaultTask() {

    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val baseline: RegularFileProperty

    @get:Input
    abstract val maxLines: Property<Int>

    @get:Input
    abstract val warnLines: Property<Int>

    @get:Internal
    abstract val repositoryRoot: DirectoryProperty

    @TaskAction
    fun check() {
        val root = repositoryRoot.get().asFile.toPath()
        val max = maxLines.get()
        val warn = warnLines.get()
        val allowed = baseline.orNull
            ?.asFile
            ?.takeIf { it.exists() }
            ?.readLines()
            .orEmpty()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        val measured = sources.files
            .filter { it.isFile && it.extension == "kt" }
            .map { file ->
                val path = root.relativize(file.toPath()).joinToString("/") { it.toString() }
                path to file.readLines().size
            }

        val violations = mutableListOf<String>()
        measured.forEach { (path, lines) ->
            when {
                lines > max && path in allowed ->
                    logger.warn("$path: $lines lines - over the $max line limit, allowed by the baseline. Split it when you next touch it.")
                lines > max ->
                    violations += "$path: $lines lines (limit $max)"
                lines > warn ->
                    logger.warn("$path: $lines lines - approaching the $max line limit.")
            }
        }

        // The baseline spans every module, so only entries this module actually
        // measured can be judged stale.
        val measuredPaths = measured.map { (path, _) -> path }.toSet()
        val overTheLimit = measured.filter { (_, lines) -> lines > max }.map { (path, _) -> path }.toSet()
        (allowed intersect measuredPaths).minus(overTheLimit).forEach {
            logger.warn("$it is under the limit now; drop it from config/file-length/baseline.txt.")
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                violations.joinToString(
                    prefix = "Kotlin files over the $max line limit:\n  ",
                    separator = "\n  ",
                    postfix = "\n\nSplit them, or - only for pre-existing files - add the path to " +
                        "config/file-length/baseline.txt. See docs/CODING_STYLE.md."
                )
            )
        }
    }
}

/**
 * Fails the build for a line of `compose-stability.conf` that no longer names a type.
 *
 * The file tells the Compose compiler which of the app's value types are immutable when
 * it cannot see that for itself - see the file's own header. Entries are matched as plain
 * strings, so a line matching nothing is an error to nobody: not the compiler, not Gradle,
 * not the build log. It is a type that quietly stopped being declared stable, and screens
 * built on it silently recomposing wholesale again. This has happened - the file's header
 * records the reader profile's types outliving the screen that used them, caught by a
 * person reading the file.
 *
 * It cannot check whether a line is still *worth* having: a class the compiler has since
 * learned to prove stable is redundant rather than broken.
 */
abstract class CheckComposeStabilityConfig : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val config: RegularFileProperty

    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /** The package prefix the check applies to; everything else in the file is a library type. */
    @get:Input
    abstract val ownPackage: Property<String>

    @TaskAction
    fun check() {
        val prefix = ownPackage.get() + "."

        val configured = config.get().asFile.readLines()
            .map { it.substringBefore("//").trim() }
            .filter { it.startsWith(prefix) }

        check(configured.isNotEmpty()) {
            "${config.get().asFile.name} names no $prefix types - the scan is broken, not the file."
        }

        val declared = sources.files
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { it.declaredTypeNames() }
            .toSet()

        val missing = configured.filterNot { it in declared }

        if (missing.isNotEmpty()) {
            throw GradleException(
                missing.joinToString(
                    prefix = "${config.get().asFile.name} names types that are no longer declared:\n  ",
                    separator = "\n  ",
                    postfix = "\n\nThe Compose compiler ignores each of these silently. Point it at where " +
                        "the type lives now, or drop the line if the type has gone."
                )
            )
        }
    }

    /**
     * The types one file declares, by fully qualified name, with nesting tracked by
     * indentation.
     *
     * Indentation rather than a parser: the only question is whether a name is declared
     * here at all, and every file is ktlint-formatted, so a nested declaration is indented
     * and a top-level one is not. A misread can only add a name that is not there, which
     * makes the check more forgiving rather than less.
     */
    private fun File.declaredTypeNames(): List<String> {
        val text = readText()
        val packageName = packagePattern.find(text)?.groupValues?.get(1) ?: return emptyList()
        val enclosing = mutableListOf<Pair<Int, String>>()

        return text.lineSequence().mapNotNull { line ->
            val (indent, name) = declaredTypePattern.find(line)?.destructured ?: return@mapNotNull null

            while (enclosing.isNotEmpty() && enclosing.last().first >= indent.length) enclosing.removeLast()

            val qualified = (listOf(packageName) + enclosing.map { it.second } + name).joinToString(".")
            enclosing += indent.length to name

            qualified
        }.toList()
    }

    private companion object {
        val packagePattern = Regex("""^package\s+([\w.]+)""", RegexOption.MULTILINE)

        /**
         * A type declaration and how far it is indented. `data class`, `value class` and
         * `data object` are covered by the modifiers before the keyword; a typealias is
         * not something the compiler can be told about.
         */
        val declaredTypePattern = Regex(
            """^([\t ]*)(?:(?:public|internal|private|protected|expect|actual|abstract|open|sealed|""" +
                """data|value|inner|enum|annotation|external|fun)\s+)*(?:class|interface|object)\s+(\w+)"""
        )
    }
}

/**
 * Fails when the attribution list, the version catalogue and `NOTICE` disagree.
 *
 * Apache-2.0 section 4 asks a distribution to carry the licence and copyright notices of
 * what it redistributes. A hand-written list is right the day it is written and wrong by
 * the next dependency; a licence plugin costs more than it settles for seventeen names -
 * see the header of `config/attribution/dependencies.txt`.
 *
 * So the list is hand-written and this stops it drifting. Every module the catalogue
 * declares is either claimed by an attribution's Maven group or listed in
 * `not-distributed.txt` with the reason it ships in nothing, making a new dependency a
 * decision rather than something forgettable. It reads both ways, as the architecture
 * allow-lists do: an attribution whose groups match nothing left the catalogue and goes
 * with it.
 *
 * **What it cannot check** is the entries that name no group - a runtime arriving
 * underneath something the catalogue does name, which the catalogue can therefore
 * neither confirm nor deny. Those are read by a person or not at all.
 */
abstract class CheckAttribution : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val attributions: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val notDistributed: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val catalogue: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val notice: RegularFileProperty

    @TaskAction
    fun check() {
        val entries = attributions.get().asFile.dataLines().map { it.split('|').map(String::trim) }
        check(entries.isNotEmpty()) { "The attribution list is empty - the scan is broken, not the file." }

        val shipped = catalogueModules() - notDistributed.get().asFile.dataLines().toSet()
        check(shipped.isNotEmpty()) { "No catalogue modules were read - the scan is broken, not the file." }

        val claimed = entries.flatMap { it[3].split(',').map(String::trim).filter(String::isNotEmpty) }
        val groups = shipped.map { it.substringBefore(':') }
        val noticeText = notice.get().asFile.readText()

        val problems =
            shipped.filterNot { module -> claimed.any { module.substringBefore(':').isUnder(it) } }
                .map { "$it ships in the app and no attribution claims its group" } +
                claimed.filterNot { group -> groups.any { it.isUnder(group) } }
                    .map { "an attribution claims $it, which the catalogue no longer declares" } +
                entries.filterNot { it[0] in noticeText && it[2] in noticeText }
                    .map { "NOTICE does not carry ${it[0]} with its copyright line" }

        if (problems.isNotEmpty()) {
            throw GradleException(
                problems.sorted().joinToString(
                    prefix = "The attribution list no longer describes what the app ships:\n  ",
                    separator = "\n  ",
                    postfix = "\n\nEvery dependency is either attributed in config/attribution/dependencies.txt " +
                        "or excused in not-distributed.txt beside it, and NOTICE carries every attribution."
                )
            )
        }
    }

    /** Each `module = "group:artifact"` the catalogue's `[libraries]` section declares. */
    private fun catalogueModules(): Set<String> {
        val libraries = catalogue.get().asFile.readText()
            .substringAfter("[libraries]", "")
            .substringBefore("\n[")

        return modulePattern.findAll(libraries).map { it.groupValues[1] }.toSet()
    }

    /**
     * Whether a Maven group is [prefix] or sits under it, by segment rather than by
     * characters - so `androidx` claims `androidx.core` and leaves
     * `org.jetbrains.androidx` to be claimed on purpose.
     */
    private fun String.isUnder(prefix: String): Boolean = this == prefix || startsWith("$prefix.")

    private fun File.dataLines(): List<String> =
        readLines().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }

    private companion object {
        val modulePattern = Regex("""module\s*=\s*"([\w.\-]+:[\w.\-]+)"""")
    }
}

// Resolved here because the version catalog accessor only exists on the root project.
val composeDetektRules = libs.detekt.composeRules

/**
 * One SARIF file for all four modules, which is what CI hands to GitHub.
 *
 * Uploaded to code scanning so a finding lands on the line that caused it in the
 * pull request, rather than inside an HTML report nobody opens. Merged because
 * the upload takes one file and detekt writes one per module.
 */
val detektReportMerge = tasks.register<ReportMergeTask>("detektReportMerge") {
    output.set(rootProject.layout.buildDirectory.file("reports/detekt/merged.sarif"))
}

/**
 * One check for the whole repository rather than one per module, because there is one
 * configuration file and it is the root project's.
 *
 * It reads every module's sources through the root's own tree rather than each project's,
 * so a type moved from :shared to another module is found where it went instead of
 * reported as gone - and so nothing here reaches across projects to configure itself.
 */
val checkComposeStability = tasks.register<CheckComposeStabilityConfig>("checkComposeStability") {
    group = "verification"
    description = "Fails on a compose-stability.conf entry that no longer names a type."
    config.set(layout.projectDirectory.file("compose-stability.conf"))
    sources.from(fileTree(layout.projectDirectory) { include("*/src/**/*.kt") })
    ownPackage.set("io.lukia")
}

/**
 * One check for the whole repository, like the one above it and for the same
 * reason: the three files it reads are the root project's, and what ships is a
 * fact about the distribution rather than about any one module.
 */
val checkAttribution = tasks.register<CheckAttribution>("checkAttribution") {
    group = "verification"
    description = "Fails when the attribution list, the version catalogue and NOTICE disagree."
    attributions.set(layout.projectDirectory.file("config/attribution/dependencies.txt"))
    notDistributed.set(layout.projectDirectory.file("config/attribution/not-distributed.txt"))
    catalogue.set(layout.projectDirectory.file("gradle/libs.versions.toml"))
    notice.set(layout.projectDirectory.file("NOTICE"))
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    val moduleName = name

    extensions.configure<DetektExtension> {
        // Start from detekt's defaults so config/detekt/detekt.yml only records what differs.
        buildUponDefaultConfig = true
        parallel = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        // One baseline per module, so regenerating one does not erase the others.
        baseline = rootProject.file("config/detekt/baseline-$moduleName.xml")
        // Kotlin Multiplatform spreads code across commonMain, jvmMain, androidMain, iosMain
        // and their test twins; detekt's default source set guesses none of them.
        source.setFrom(files("src"))
    }

    extensions.configure<KtlintExtension> {
        version.set("1.6.0")
        // Rules and their options live in .editorconfig, the file the IDE reads too.
        android.set(false)
        filter {
            exclude { it.file.path.contains("${File.separator}build${File.separator}") }
        }
    }

    dependencies {
        add("detektPlugins", composeDetektRules)
    }

    tasks.withType<Detekt>().configureEach {
        reports {
            html.required.set(true)
            xml.required.set(false)
            // Read by CI, which turns it into annotations on the pull request.
            sarif.required.set(true)
            md.required.set(false)
        }
        finalizedBy(detektReportMerge)
        detektReportMerge.configure { input.from(sarifReportFile) }
    }

    val checkFileLength = tasks.register<CheckKotlinFileLength>("checkFileLength") {
        group = "verification"
        description = "Fails on Kotlin files longer than the project's line limit."
        sources.from(fileTree("src") { include("**/*.kt") })
        baseline.set(rootProject.layout.projectDirectory.file("config/file-length/baseline.txt"))
        maxLines.set(400)
        warnLines.set(350)
        repositoryRoot.set(rootProject.layout.projectDirectory)
    }

    // `check` only exists once the module's own plugins have been applied.
    plugins.withType<LifecycleBasePlugin> {
        // The stability and attribution checks are the root project's and cover
        // every module, so whichever `check` runs first is the one that runs them.
        tasks.named("check") { dependsOn(checkFileLength, checkComposeStability, checkAttribution) }
    }
}
