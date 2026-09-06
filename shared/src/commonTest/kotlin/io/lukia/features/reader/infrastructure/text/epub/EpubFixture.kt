package io.lukia.features.reader.infrastructure.text.epub

import io.lukia.core.infrastructure.archive.storedZip
import io.lukia.features.reader.domain.ReflowableContent

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

/**
 * The archive, with whatever a test wrote into it as text or as bytes.
 *
 * Written by [storedZip] rather than by `java.util.zip`, which is what moved
 * this whole set of tests out of `jvmTest`: an EPUB is opened on a phone far
 * more often than on a desktop, and until this changed no test ever opened one
 * anywhere but the JVM.
 */
internal fun zip(entries: Map<String, Any>): ByteArray = storedZip(
    entries.mapValues { (_, content) ->
        when (content) {
            is ByteArray -> content
            else -> content.toString().encodeToByteArray()
        }
    },
)

/**
 * [readEpub] under whatever scope the test is running in.
 *
 * The files really are read at the same time as each other here as they are in
 * the app - a helper that read them one after another would be checking a
 * different reader to the one that ships.
 */
internal suspend fun epubRead(bytes: ByteArray): ReflowableContent? = readEpub(bytes)
