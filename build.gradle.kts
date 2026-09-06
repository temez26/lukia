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
                        "config/file-length/baseline.txt. See CODING_STYLE.md."
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
 * strings, and a line that matches nothing is not an error to anybody: not to the
 * compiler, not to Gradle, and not to a build log. It is a type that quietly stopped being
 * declared stable, and screens built on it that went back to recomposing wholesale with
 * nothing on screen to say so.
 *
 * Which has happened. The file's own header records the reader profile's types sitting in
 * it for a while after the statistics screen replaced them, noticed by somebody reading
 * the file rather than by anything failing.
 *
 * What it cannot check is whether a line is still worth having. A class the compiler has
 * since learned to prove stable by itself is redundant rather than broken, and nothing
 * about that is wrong.
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
     * Indentation rather than a parser, because the only question is whether a name is
     * declared here at all, and every file in the project is ktlint-formatted - so a
     * nested declaration is indented and a top-level one is not. A misread can only add a
     * name that is not really there, which makes the check more forgiving rather than
     * less: it can miss a stale line, and it cannot invent one.
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
        // The stability check is the root project's and covers every module, so
        // whichever `check` runs first is the one that runs it.
        tasks.named("check") { dependsOn(checkFileLength, checkComposeStability) }
    }
}
