import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
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

// Resolved here because the version catalog accessor only exists on the root project.
val composeDetektRules = libs.detekt.composeRules

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
            sarif.required.set(false)
            md.required.set(false)
        }
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
        tasks.named("check") { dependsOn(checkFileLength) }
    }
}
