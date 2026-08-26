package io.lukia.features.library.infrastructure.importing.cover

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * An EPUB archive built around [opf], carrying [coverBytes] at [coverPath] when
 * it is meant to have a cover at all.
 *
 * A real archive rather than a stand-in reader: what the extraction is for is
 * finding a cover inside a zip somebody else wrote, and a fake would answer that
 * with whatever it was written to believe.
 */
internal fun testEpub(
    opf: String,
    coverPath: String?,
    coverBytes: ByteArray?,
): ByteArray {
    val output = ByteArrayOutputStream()

    ZipOutputStream(output).use { zip ->
        fun addEntry(name: String, content: ByteArray) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content)
            zip.closeEntry()
        }

        addEntry(
            "META-INF/container.xml",
            """
                <?xml version="1.0"?>
                <container>
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
            """.trimIndent().encodeToByteArray(),
        )

        addEntry("OEBPS/content.opf", opf.encodeToByteArray())

        if (coverPath != null && coverBytes != null) {
            addEntry(coverPath, coverBytes)
        }
    }

    return output.toByteArray()
}
