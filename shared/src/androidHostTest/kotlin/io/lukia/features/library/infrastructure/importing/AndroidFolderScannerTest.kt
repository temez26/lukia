package io.lukia.features.library.infrastructure.importing

import android.content.Context
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import io.lukia.core.domain.document.PickedFolder
import io.lukia.features.library.domain.MaxFolderScanDepth
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Walking a folder somebody granted through the picker, on Android.
 *
 * `JvmFolderScannerTest` pins the same rules against a directory on disk; this
 * pins them against a storage provider, Android's only way of reaching one. The
 * two share nothing - one lists a `File`, the other pages a cursor per folder -
 * so a rule proved on the desktop says nothing about the platform most books are
 * imported on. On the host: a provider answers a cursor the same way either way.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidFolderScannerTest {

    private val scanner = AndroidFolderScanner(ApplicationProvider.getApplicationContext<Context>())

    @BeforeTest
    fun registerProvider() {
        Robolectric.setupContentProvider(
            TestDocumentsProvider::class.java,
            TestDocumentsProvider.AUTHORITY,
        )
    }

    @AfterTest
    fun tidy() = TestDocumentsProvider.reset()

    @Test
    fun findsSupportedDocumentsAndIgnoresEverythingElse() = runTest {
        TestDocumentsProvider.tree = mapOf(
            "root" to listOf(
                file("book.pdf"),
                file("novel.epub"),
                file("report.docx"),
                file("notes.txt"),
                file("readme.md"),
                file("cover.jpg"),
                file("sequel.mobi"),
            ),
        )

        assertEquals(
            setOf("book.pdf", "novel.epub", "report.docx", "notes.txt", "readme.md"),
            scanner.scan(rootFolder()).map { it.fileName }.toSet(),
        )
    }

    /** A shelf on a phone is organised, not flat. */
    @Test
    fun findsBooksFiledInSubFolders() = runTest {
        TestDocumentsProvider.tree = mapOf(
            "root" to listOf(file("loose.pdf"), folder("Le Guin")),
            "root/Le Guin" to listOf(folder("Earthsea")),
            "root/Le Guin/Earthsea" to listOf(file("wizard.epub")),
        )

        assertEquals(
            setOf("loose.pdf", "wizard.epub"),
            scanner.scan(rootFolder()).map { it.fileName }.toSet(),
        )
    }

    @Test
    fun matchesExtensionsRegardlessOfCase() = runTest {
        TestDocumentsProvider.tree = mapOf("root" to listOf(file("BOOK.PDF")))

        assertEquals(listOf("BOOK.PDF"), scanner.scan(rootFolder()).map { it.fileName })
    }

    /**
     * A folder that links back to one of its own parents would otherwise be
     * walked forever, so the scan stops at a fixed depth. A provider can do this
     * to itself far more easily than a filesystem can.
     */
    @Test
    fun stopsDescendingAtTheDepthCap() = runTest {
        val levels = MaxFolderScanDepth + 2
        var path = "root"

        TestDocumentsProvider.tree = buildMap {
            repeat(levels) { level ->
                put(path, listOf(file("book$level.pdf"), folder("level$level")))
                path = "$path/level$level"
            }
        }

        val found = scanner.scan(rootFolder()).map { it.fileName }.toSet()

        assertTrue("book0.pdf" in found)
        assertFalse("book${MaxFolderScanDepth + 1}.pdf" in found)
    }

    /**
     * One sub-folder the provider will not open should cost its own contents and
     * not the whole import - a reader with three hundred books and one bad
     * folder wants the two hundred and ninety-nine.
     */
    @Test
    fun aSubFolderTheProviderRefusesCostsOnlyItsOwnContents() = runTest {
        TestDocumentsProvider.tree = mapOf(
            "root" to listOf(file("readable.pdf"), folder("locked")),
        )
        TestDocumentsProvider.refusing = setOf("root/locked")

        assertEquals(listOf("readable.pdf"), scanner.scan(rootFolder()).map { it.fileName })
    }

    /** A provider is allowed to return a row missing what makes a child addressable. */
    @Test
    fun aRowWithNoNameIsSkippedRatherThanImported() = runTest {
        TestDocumentsProvider.tree = mapOf(
            "root" to listOf(file("book.pdf"), file("ghost.pdf", nameless = true)),
        )

        assertEquals(listOf("book.pdf"), scanner.scan(rootFolder()).map { it.fileName })
    }

    @Test
    fun aFolderWithNoBooksInItYieldsNone() = runTest {
        TestDocumentsProvider.tree = mapOf("root" to listOf(file("cover.jpg")))

        assertTrue(scanner.scan(rootFolder()).isEmpty())
    }

    /** Something that is not a tree URI at all is a scan with nothing to walk. */
    @Test
    fun somethingThatIsNotAFolderYieldsNothing() = runTest {
        assertTrue(scanner.scan(PickedFolder(name = "books", location = "not a uri")).isEmpty())
    }

    /**
     * Every document is addressed the way the file picker addresses one, so the
     * analyser can resolve what the scan found without knowing where it came
     * from.
     */
    @Test
    fun everyBookIsAddressedTheWayThePickerAddressesOne() = runTest {
        TestDocumentsProvider.tree = mapOf("root" to listOf(file("book.pdf")))

        val document = scanner.scan(rootFolder()).single()

        assertEquals(
            DocumentsContract.buildDocumentUriUsingTree(treeUri(), "root/book.pdf").toString(),
            document.location,
        )
    }

    private fun file(name: String, nameless: Boolean = false) =
        TestDocumentsProvider.Child(name, isFolder = false, nameless = nameless)

    private fun folder(name: String) = TestDocumentsProvider.Child(name, isFolder = true)

    private fun treeUri() = DocumentsContract.buildTreeDocumentUri(
        TestDocumentsProvider.AUTHORITY,
        "root",
    )

    private fun rootFolder() = PickedFolder(name = "books", location = treeUri().toString())
}
