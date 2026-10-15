package io.lukia.core.infrastructure.diagnostics

import io.lukia.core.domain.document.DocumentType
import io.lukia.core.infrastructure.importing.extractContents
import io.lukia.core.infrastructure.importing.openedDocument
import io.lukia.features.reader.infrastructure.text.decodeText
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Everything a reader would mind being handed to somebody else, spelled as the
 * literal strings a leak would carry.
 *
 * A library is a political and a medical record, and a desktop path names the
 * person whose folder it is in - so a title, an author and a file name are not
 * lesser versions of a password, they are the thing this app exists to keep on
 * the device.
 */
private const val Title = "Anna-Liisa"
private const val Author = "Minna Canth"
private const val FileName = "Anna-Liisa - Minna Canth.epub"
private const val Path = "/Users/anna-liisa/Documents/Books"

/**
 * The test that has to exist before any of this is shown to anybody.
 *
 * `causeType` and a constant `event` are what make a leak difficult, but neither
 * makes it impossible: `detail` is a `String?` and nothing in the compiler stops
 * the next person from interpolating a file name into one. This is what fails on
 * the day they do, rather than on the day a report is handed over.
 */
class DiagnosticsRedactionTest {

    @Test
    fun nothingRecordedByTheImportNamesTheBook() {
        val buffer = DiagnosticBuffer()
        val diagnostics = RecordingDiagnostics(buffer)

        // Damaged on purpose, and carrying every identifying string in its
        // bytes - so both extractions fail with the title and the author within
        // reach of whatever gets written down.
        openedDocument(damagedBook()) { extractContents(DocumentType.EPUB, it, diagnostics) }
        openedDocument(damagedBook()) { extractContents(DocumentType.PDF, it, diagnostics) }
        openedDocument(damagedBook()) { extractContents(DocumentType.DOCX, it, diagnostics) }

        assertRecordedNothingIdentifying(buffer)
    }

    @Test
    fun nothingRecordedByTheReaderNamesTheBook() {
        val buffer = DiagnosticBuffer()
        val diagnostics = RecordingDiagnostics(buffer)

        // Windows-1252 punctuation, which is not valid UTF-8 - so the decoder
        // guesses and records, holding the whole of the book's text as it does.
        val windows1252 = ("$Title, by $Author".encodeToByteArray() + byteArrayOf(0x97.toByte()))

        decodeText(windows1252, diagnostics)

        assertRecordedNothingIdentifying(buffer)
    }

    private fun assertRecordedNothingIdentifying(buffer: DiagnosticBuffer) {
        val entries = buffer.entries.value

        // Without this the test passes for the wrong reason the day a fixture
        // stops failing, and goes on passing while the guard guards nothing.
        assertTrue(entries.isNotEmpty(), "nothing was recorded, so this test proved nothing")

        val recorded = entries.joinToString("\n") {
            "${it.area} ${it.event} ${it.detail.orEmpty()} ${it.causeType.orEmpty()}"
        }

        // Both the fields and the report built from them. The report is the
        // thing that actually leaves the device, and it is assembled by
        // different code from the fields - so checking the entries alone would
        // stop guarding the moment the formatter learned to add something.
        val report = diagnosticReport(entries, platformName = "Android 34")

        listOf(Title, Author, FileName, Path, "/", "\\").forEach { secret ->
            assertFalse(
                recorded.contains(secret),
                "diagnostics leaked \"$secret\":\n$recorded",
            )

            assertFalse(
                report.contains(secret),
                "the copied report leaked \"$secret\":\n$report",
            )
        }
    }

    /** A zip whose central directory has been cut off, with the book's own words inside it. */
    private fun damagedBook(): ByteArray {
        val output = ByteArrayOutputStream()

        ZipOutputStream(output).use { stream ->
            stream.putNextEntry(ZipEntry("META-INF/container.xml"))
            stream.write("<container><rootfile full-path=\"$FileName\"/></container>".encodeToByteArray())
            stream.closeEntry()

            stream.putNextEntry(ZipEntry(FileName))
            stream.write("<dc:title>$Title</dc:title><dc:creator>$Author</dc:creator>".encodeToByteArray())
            stream.closeEntry()
        }

        val whole = output.toByteArray()

        return whole.copyOfRange(0, whole.size / 2)
    }
}
