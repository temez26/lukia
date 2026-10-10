package io.lukia.di

import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * Holds the three platform modules to the same set of bindings.
 *
 * [AppGraphTest] resolves a graph, the stronger check, but only for one
 * platform: it runs on the JVM and builds `jvmMain`'s `platformModule`, where
 * Android's needs a `Context` and iOS's a keychain. So a registration Android
 * forgot is otherwise found by opening the screen that needs it, on a device.
 *
 * Read as text for the same reason [io.lukia.ArchitectureTest] is: what is being
 * checked is what each file declares, and reading the declarations needs neither
 * a device nor the platform's own compiler.
 *
 * Only that the same types are bound - not that they are bound to anything
 * sensible. A type present on two platforms and absent on the third is the
 * mistake that actually happens, because a platform is added by copying the file
 * beside it.
 */
class PlatformModuleParityTest {

    /**
     * Bound by the desktop and by nothing else, because no other platform has a
     * window whose size is the app's to remember. Listed rather than tolerated:
     * an entry that stops being desktop-only fails here as stale.
     */
    private val desktopOnly = setOf("DesktopWindowBounds")

    @Test
    fun everyPlatformBindsTheSameTypes() {
        val modules = platformModules()
        val common = modules.values.reduce { shared, bindings -> shared intersect bindings }

        val report = modules.mapNotNull { (platform, bindings) ->
            val extra = bindings - common - desktopOnly
            val stale = if (platform == "jvm") desktopOnly - bindings else emptySet()

            when {
                extra.isEmpty() && stale.isEmpty() -> null
                else -> buildString {
                    append("  $platform")
                    if (extra.isNotEmpty()) {
                        append("\n    binds, and no other platform does: ${extra.sorted()}")
                    }
                    if (stale.isNotEmpty()) {
                        append("\n    no longer binds, so it is not desktop-only: ${stale.sorted()}")
                    }
                }
            }
        }

        if (report.isNotEmpty()) {
            fail(
                report.joinToString(
                    separator = "\n",
                    prefix = "Every platform module binds the same types, but:\n",
                ),
            )
        }
    }

    /**
     * Guards the check above rather than the modules: an intersection of nothing
     * is empty, and a scan that quietly matched no bindings would pass every
     * assertion here while reading no code at all.
     */
    @Test
    fun theScanFindsTheBindingsItIsAbout() {
        platformModules().forEach { (platform, bindings) ->
            if (bindings.size < MIN_PLAUSIBLE_BINDINGS) {
                fail("Only ${bindings.size} bindings found in $platform's module - the scan is broken.")
            }
        }
    }
}

/** Enough that a scan reading nothing cannot be mistaken for a module declaring little. */
private const val MIN_PLAUSIBLE_BINDINGS = 5

/**
 * What each platform's `PlatformModule.kt` binds, by the name of the type.
 *
 * Both forms count. `single<SecretStore> { ... }` names the type it is bound to;
 * `single { DesktopWindowBounds(get()) }` leaves Koin to infer it from what is
 * constructed, and the constructor is then the name.
 */
private fun platformModules(): Map<String, Set<String>> =
    listOf("android", "ios", "jvm").associateWith { platform ->
        val source = File(repositoryRoot, "shared/src/${platform}Main/kotlin/io/lukia/di/PlatformModule.kt")

        if (!source.isFile) error("No platform module at $source - the scan is broken, not the graph.")

        bindingPattern.findAll(source.readText())
            .map { it.groupValues[1].ifEmpty { it.groupValues[2] } }
            .toSet()
    }

/** `single<Type> {` or, where the type is left to be inferred, `single {` and its constructor. */
private val bindingPattern = Regex("""single<(\w+)>\s*\{|single\s*\{\s*(?://[^\n]*\n\s*)*(\w+)\(""")

private val repositoryRoot: File =
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "shared/src").isDirectory }
        ?: error("No shared/src above ${System.getProperty("user.dir")} - the test cannot find the sources.")
