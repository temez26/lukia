package io.lukia.features.import.infrastructure.cover

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EpubCoverExtractorTest {

    @Test
    fun extractsCoverDeclaredViaEpub2MetaTag() {
        val coverBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)

        val epubBytes = buildEpub(
            opf = """
                <?xml version="1.0"?>
                <package xmlns="http://www.idpf.org/2007/opf">
                  <metadata>
                    <meta name="cover" content="cover-image"/>
                  </metadata>
                  <manifest>
                    <item id="cover-image" href="images/cover.jpg" media-type="image/jpeg"/>
                  </manifest>
                </package>
            """.trimIndent(),
            coverPath = "OEBPS/images/cover.jpg",
            coverBytes = coverBytes
        )

        assertEquals(coverBytes.toList(), extractEpubCover(epubBytes)?.toList())
    }

    @Test
    fun extractsCoverDeclaredViaEpub3Properties() {
        val coverBytes = byteArrayOf(9, 8, 7, 6, 5)

        val epubBytes = buildEpub(
            opf = """
                <?xml version="1.0"?>
                <package xmlns="http://www.idpf.org/2007/opf">
                  <metadata/>
                  <manifest>
                    <item id="cover-image" href="cover.png" media-type="image/png" properties="cover-image"/>
                  </manifest>
                </package>
            """.trimIndent(),
            coverPath = "OEBPS/cover.png",
            coverBytes = coverBytes
        )

        assertEquals(coverBytes.toList(), extractEpubCover(epubBytes)?.toList())
    }

    @Test
    fun returnsNullWhenNoCoverIsDeclared() {
        val epubBytes = buildEpub(
            opf = """
                <?xml version="1.0"?>
                <package xmlns="http://www.idpf.org/2007/opf">
                  <metadata/>
                  <manifest>
                    <item id="chapter1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                </package>
            """.trimIndent(),
            coverPath = null,
            coverBytes = null
        )

        assertNull(extractEpubCover(epubBytes))
    }

    @Test
    fun returnsNullForMalformedArchive() {
        assertNull(extractEpubCover("not an epub".encodeToByteArray()))
    }

    private fun buildEpub(
        opf: String,
        coverPath: String?,
        coverBytes: ByteArray?
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
                """.trimIndent().encodeToByteArray()
            )

            addEntry("OEBPS/content.opf", opf.encodeToByteArray())

            if (coverPath != null && coverBytes != null) {
                addEntry(coverPath, coverBytes)
            }
        }
        return output.toByteArray()
    }
}
