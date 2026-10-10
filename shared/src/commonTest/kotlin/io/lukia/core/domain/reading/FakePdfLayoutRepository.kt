package io.lukia.core.domain.reading

/**
 * Choices in a map, for the tests that only need somewhere for one to go. What
 * the real store does with SQL is covered against a real database in
 * `SqlPdfLayoutRepositoryTest`.
 */
class FakePdfLayoutRepository(
    private val layouts: MutableMap<String, PdfLayout> = mutableMapOf(),
) : PdfLayoutRepository {

    override suspend fun layoutFor(location: String): PdfLayout? = layouts[location]

    override suspend fun save(location: String, layout: PdfLayout) {
        layouts[location] = layout
    }

    override suspend fun forget(location: String) {
        layouts.remove(location)
    }

    /** What is stored for a book, or null when nothing is - which a caller can see. */
    fun stored(location: String): PdfLayout? = layouts[location]
}
