package io.lukia.features.reader.presentation

import androidx.lifecycle.ViewModelStore
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType
import io.lukia.core.domain.FakeSecretStore
import io.lukia.core.domain.FakeSettingsStore
import io.lukia.core.domain.PickedDocument
import io.lukia.core.infrastructure.JvmDocumentWorkspace
import io.lukia.core.infrastructure.testDatabase
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.dataOrNull
import io.lukia.features.library.domain.LibraryRepository
import io.lukia.features.reader.application.OpenDocumentUseCase
import io.lukia.features.reader.domain.FakePdfLayoutRepository
import io.lukia.features.reader.domain.ReaderDocument
import io.lukia.features.reader.domain.ReaderOutline
import io.lukia.features.reader.infrastructure.ReaderSettingsRepository
import io.lukia.features.reader.infrastructure.SqlReadingProgressRepository
import io.lukia.features.translate.application.TranslatePassageUseCase
import io.lukia.features.translate.domain.FakeTranslator
import io.lukia.features.translate.infrastructure.TranslationSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/**
 * The world a reader ViewModel test needs: a temporary library on disk, a real
 * position store, and a ViewModel store to cancel through.
 *
 * A base class rather than a fixture object because the teardown is the
 * delicate part - it has to let each ViewModel finish before taking its files
 * away - and inheriting it is what stops a test class from forgetting half of
 * it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class ReaderViewModelTestCase {

    protected val root = File(
        System.getProperty("java.io.tmpdir"),
        "lukia-reader-test-${kotlin.random.Random.nextInt(until = Int.MAX_VALUE)}",
    )
    protected val library = File(root, "library").also { it.mkdirs() }
    protected val workspace = JvmDocumentWorkspace(directory = File(root, "workspace"))

    /**
     * Opening a document reads a real file and parses a real PDF. There is no
     * virtual time to advance, and the copy step hands off to [Dispatchers.IO]
     * on the way - so these tests wait for the state to settle through
     * [settled] rather than pretending the work is synchronous.
     */
    protected val openDispatcher = Dispatchers.Unconfined

    /**
     * The real store against an in-memory database rather than a fake: where a
     * book opens is the point of this, and a fake would only ever return what
     * the fake was told to.
     */
    protected val progress = SqlReadingProgressRepository(
        database = testDatabase(),
        dispatcher = UnconfinedTestDispatcher(),
    )

    /**
     * Every ViewModel a test opens, held in a store so they can all be cleared
     * afterwards - [ViewModelStore.clear] is the way to cancel a ViewModel's
     * scope from outside, since the ViewModel's own `clear` is internal.
     */

    /** How each book is read. Nothing has chosen, so every PDF opens as pages. */
    protected val layouts = FakePdfLayoutRepository()

    protected val viewModelStore = ViewModelStore()
    protected val openedViewModels = mutableListOf<ReaderViewModel>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    /**
     * Lets each ViewModel finish before taking its world away.
     *
     * Opening a book starts two pieces of real work - the copy and then the
     * outline - and cancelling a coroutine that is inside a blocking read does
     * not stop it: it still has to resume, on a main dispatcher that
     * [resetMain] would by then have removed. Waiting for the work to land
     * first is what makes that impossible rather than unlikely.
     *
     * The open document is closed for the same kind of reason: its file handle
     * has to be given back before the directory underneath it goes.
     */
    @AfterTest
    fun tearDown() {
        runBlocking {
            withTimeoutOrNull(SETTLE_TIMEOUT_MILLIS) {
                openedViewModels.forEach { viewModel ->
                    val state = viewModel.uiState.first { it !is UiState.Loading }

                    // Only a book that opened goes on to read an outline.
                    if (state is UiState.Success) {
                        viewModel.outline.first { it !is ReaderOutline.Loading }
                    }
                }
            }
        }

        viewModelStore.clear()
        openedViewModels.forEach { it.uiState.value.dataOrNull?.close() }

        Dispatchers.resetMain()
        root.deleteRecursively()
    }

    protected fun track(viewModel: ReaderViewModel): ReaderViewModel {
        viewModelStore.put("reader-${openedViewModels.size}", viewModel)
        openedViewModels += viewModel
        return viewModel
    }

    /** The state once the document has finished opening, one way or the other. */
    protected suspend fun ReaderViewModel.settled(): UiState<ReaderDocument> =
        uiState.first { it !is UiState.Loading }

    protected fun createViewModel(
        document: DocumentInfo,
        /** Supplied where a test cares what new books are set to open as. */
        settings: ReaderSettingsRepository = ReaderSettingsRepository(FakeSettingsStore()),
    ) = ReaderViewModel(
        location = document.document.location,
        openDocumentUseCase = OpenDocumentUseCase(
            libraryRepository = FakeLibraryRepository(listOf(document)),
            documentWorkspace = workspace,
            dispatcher = openDispatcher,
        ),
        settingsRepository = settings,
        progressRepository = progress,
        layoutRepository = layouts,
        translations = testTranslations(),
    ).also { track(it) }

    /** A translator that answers, wired the way the app wires the real one. */
    protected fun testTranslations() = ReaderTranslationsFactory(
        TranslatePassageUseCase(FakeTranslator()),
        TranslationSettingsRepository(FakeSettingsStore(), FakeSecretStore()),
    )

    /**
     * A PDF in the library. [text] is written on every page where it is given,
     * which is what makes the difference between a document that can be read as
     * text and a scan - a file of blank pages has no text layer at all.
     */
    protected fun pdfInLibrary(
        pageCount: Int,
        chapters: Int = 0,
        text: String? = null,
    ): DocumentInfo {
        val file = File(library, "book-${kotlin.random.Random.nextInt()}.pdf")

        PDDocument().use { document ->
            repeat(pageCount) { index ->
                val page = PDPage(PDRectangle.A4).also(document::addPage)
                if (text != null) document.write(page, "$text ${index + 1}.")
            }

            if (chapters > 0) {
                val outline = PDDocumentOutline()
                document.documentCatalog.documentOutline = outline

                repeat(chapters) { index ->
                    outline.addLast(
                        PDOutlineItem().apply {
                            title = "Chapter ${index + 1}"
                            destination = PDPageFitWidthDestination().apply {
                                page = document.getPage(index % pageCount)
                            }
                        },
                    )
                }
            }

            document.save(file)
        }

        return DocumentInfo(
            document = PickedDocument(
                fileName = file.name,
                extension = "pdf",
                location = file.absolutePath,
            ),
            type = DocumentType.PDF,
            exists = true,
            sizeBytes = file.length(),
        )
    }

    private fun PDDocument.write(page: PDPage, text: String) {
        PDPageContentStream(this, page).use { content ->
            content.beginText()
            content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
            content.newLineAtOffset(60f, 700f)
            content.showText(text)
            content.endText()
        }
    }

    private companion object {
        /** Generous: the work is a few small files, not a real book. */
        const val SETTLE_TIMEOUT_MILLIS = 10_000L
    }

    protected class FakeLibraryRepository(
        initialDocuments: List<DocumentInfo> = emptyList(),
    ) : LibraryRepository {
        private val documents = initialDocuments.toMutableList()

        override suspend fun addDocument(document: DocumentInfo) {
            documents.add(document)
        }

        override suspend fun getDocuments(): List<DocumentInfo> = documents.toList()

        override suspend fun getDocument(location: String): DocumentInfo? =
            documents.firstOrNull { it.document.location == location }

        /** Answered the expensive way; nothing in a test cares what it costs. */
        override suspend fun knownLocations(): Set<String> =
            getDocuments().mapTo(mutableSetOf()) { it.document.location }

        override suspend fun setFavorite(location: String, isFavorite: Boolean) = Unit

        override suspend fun removeDocument(location: String) {
            documents.removeAll { it.document.location == location }
        }
    }
}
