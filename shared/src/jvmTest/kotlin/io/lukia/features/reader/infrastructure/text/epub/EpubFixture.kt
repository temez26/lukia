package io.lukia.features.reader.infrastructure.text.epub

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal fun chapter(title: String, body: String): String =
    "<html><body><h1>$title</h1><p>$body</p></body></html>"

/**
 * An EPUB with the indirection a real one has: a container naming a package
 * document, and a package document in a sub-directory so that every href in
 * the tests has somewhere to be resolved against.
 */
internal fun epub(
    manifest: String,
    spine: String,
    entries: Map<String, Any>,
): ByteArray {
    val container = """
        <?xml version="1.0"?>
        <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles>
            <rootfile full-path="OEBPS/content.opf"
                      media-type="application/oebps-package+xml"/>
          </rootfiles>
        </container>
    """.trimIndent()

    val opf = """
        <?xml version="1.0"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
          <metadata/>
          <manifest>
        $manifest
          </manifest>
          <spine>
        $spine
          </spine>
        </package>
    """.trimIndent()

    return zip(
        mapOf(
            "META-INF/container.xml" to container,
            "OEBPS/content.opf" to opf,
        ) + entries,
    )
}

internal fun zip(entries: Map<String, Any>): ByteArray {
    val output = ByteArrayOutputStream()

    ZipOutputStream(output).use { stream ->
        entries.forEach { (name, content) ->
            stream.putNextEntry(ZipEntry(name))
            stream.write(
                when (content) {
                    is ByteArray -> content
                    else -> content.toString().encodeToByteArray()
                },
            )
            stream.closeEntry()
        }
    }

    return output.toByteArray()
}
