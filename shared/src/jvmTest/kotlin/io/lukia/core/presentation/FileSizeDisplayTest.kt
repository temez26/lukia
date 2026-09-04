package io.lukia.core.presentation

import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.TestDocuments
import io.lukia.shared.composed
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A file's size, said the way the reader's own locale says numbers.
 *
 * In `jvmTest` rather than beside the rest of the document tests for the two
 * reasons that arrived together: the unit is a string resource and so needs a
 * composition, and the figure is formatted by the platform and so needs a locale
 * to be set.
 */
class FileSizeDisplayTest {

    private val wasDefault: Locale = Locale.getDefault()

    @BeforeTest
    fun useEnglish() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restoreLocale() = Locale.setDefault(wasDefault)

    @Test
    fun formattedSizeDelegatesToFormatFileSize() {
        val documentInfo = DocumentInfo(
            document = TestDocuments.pdf(location = "/test/book.pdf"),
            type = DocumentType.PDF,
            exists = true,
            sizeBytes = 2048L,
        )

        assertEquals(
            composed { formatFileSize(2048L) },
            composed { documentInfo.formattedSize() },
        )
    }

    @Test
    fun usesBytesBelowOneKilobyte() {
        assertEquals("512 B", composed { formatFileSize(512L) })
    }

    @Test
    fun usesKilobytes() {
        assertEquals("1.5 KB", composed { formatFileSize(1536L) })
    }

    @Test
    fun usesMegabytes() {
        assertEquals("2 MB", composed { formatFileSize(2 * 1024 * 1024L) })
    }

    @Test
    fun usesGigabytes() {
        assertEquals("1 GB", composed { formatFileSize(1024L * 1024L * 1024L) })
    }

    @Test
    fun handlesZero() {
        assertEquals("0 B", composed { formatFileSize(0L) })
    }

    /**
     * The reason the figure stopped being built with string interpolation. Most
     * of Europe writes the decimal mark as a comma, and "1.5 MB" was a full stop
     * wherever the app ran.
     *
     * Both halves show here now that German is translated: the comma comes from
     * the platform's formatter and the "kB" from `values-de`. It read "1,5 KB"
     * while German had only the number formatting and fell back to the English
     * unit, which is what this asserted before.
     */
    @Test
    fun theDecimalMarkFollowsTheLocale() {
        Locale.setDefault(Locale.GERMAN)

        assertEquals("1,5\u00A0kB", composed { formatFileSize(1536L) })
    }
}
