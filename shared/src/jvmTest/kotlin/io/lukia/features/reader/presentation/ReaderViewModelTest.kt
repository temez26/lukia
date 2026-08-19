package io.lukia.features.reader.presentation

import androidx.lifecycle.ViewModelStore
import io.lukia.core.domain.AppError
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType
import io.lukia.core.domain.FakeSecretStore
import io.lukia.core.domain.FakeSettingsStore
import io.lukia.core.domain.PickedDocument
import io.lukia.core.infrastructure.JvmDocumentWorkspace
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.dataOrNull
import io.lukia.features.library.domain.LibraryRepository
import io.lukia.features.reader.application.OpenDocumentUseCase
import io.lukia.features.reader.domain.ReaderDocument
import io.lukia.features.reader.domain.ReaderOutline
import io.lukia.features.reader.domain.ReaderTheme
import io.lukia.core.infrastructure.testDatabase
import io.lukia.features.reader.domain.ReadingProgress
import io.lukia.features.reader.infrastructure.ReaderSettingsRepository
import io.lukia.features.reader.infrastructure.SqlReadingProgressRepository
import io.lukia.features.translate.application.TranslatePassageUseCase
import io.lukia.features.translate.domain.FakeTranslator
import io.lukia.features.translate.infrastructure.TranslationSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    private val root = File(
        System.getProperty("java.io.tmpdir"),
        "lukia-reader-test-${kotlin.random.Random.nextInt(until = Int.MAX_VALUE)}"
    )
    private val library = File(root, "library").also { it.mkdirs() }
    private val workspace = JvmDocumentWorkspace(directory = File(root, "workspace"))

    /**
     * Opening a document reads a real file and parses a real PDF. There is no
     * virtual time to advance, and the copy step hands off to [Dispatchers.IO]
     * on the way - so these tests wait for the state to settle through
     * [settled] rather than pretending the work is synchronous.
     */
    private val openDispatcher = Dispatchers.Unconfined

    /**
     * The real store against an in-memory database rather than a fake: where a
     * book opens is the point of this, and a fake would only ever return what
     * the fake was told to.
     */
    private val progress = SqlReadingProgressRepository(
        database = testDatabase(),
        dispatcher = UnconfinedTestDispatcher()
    )

    /**
     * Every ViewModel a test opens, held in a store so they can all be cleared
     * afterwards - [ViewModelStore.clear] is the way to cancel a ViewModel's
     * scope from outside, since the ViewModel's own `clear` is internal.
     */
    private val viewModelStore = ViewModelStore()
    private val openedViewModels = mutableListOf<ReaderViewModel>()

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

    @Test
    fun opensTheDocumentOnInit() = runTest {
        val document = pdfInLibrary(pageCount = 5)
        val viewModel = createViewModel(document)

        val opened = viewModel.settled().dataOrNull

        assertNotNull(opened)
        assertEquals(5, opened.content.unitCount)
    }

    /** Reading has to happen against the copy, never the imported file. */
    @Test
    fun readsFromTheWorkingCopyRatherThanTheOriginal() = runTest {
        val document = pdfInLibrary(pageCount = 1)
        val viewModel = createViewModel(document)

        val opened = assertNotNull(viewModel.settled().dataOrNull)

        assertEquals(document.document.location, opened.workingCopy.sourceLocation)
        assertTrue(opened.workingCopy.path != document.document.location)
        assertTrue(File(opened.workingCopy.path).isFile)
    }

    @Test
    fun aLocationWithNoMatchBecomesDocumentNotFound() = runTest {
        val viewModel = ReaderViewModel(
            location = "/library/missing.pdf",
            openDocumentUseCase = OpenDocumentUseCase(
                libraryRepository = FakeLibraryRepository(),
                documentWorkspace = workspace,
                dispatcher = openDispatcher
            ),
            settingsRepository = ReaderSettingsRepository(FakeSettingsStore()),
            progressRepository = progress,
            translatePassage = TranslatePassageUseCase(FakeTranslator()),
            translationSettings = TranslationSettingsRepository(FakeSettingsStore(), FakeSecretStore())
        ).also { track(it) }

        assertEquals(UiState.Failure(AppError.DocumentNotFound), viewModel.settled())
    }

    /**
     * An EPUB is a book the library legitimately holds and the reader cannot
     * open yet, which is not the same as a file the app never accepted.
     */
    @Test
    fun aFormatTheReaderCannotOpenYetSaysSo() = runTest {
        val epub = File(library, "book.epub").apply { writeText("not read yet") }
        val document = DocumentInfo(
            document = PickedDocument(
                fileName = epub.name,
                extension = "epub",
                location = epub.absolutePath
            ),
            type = DocumentType.EPUB,
            exists = true,
            sizeBytes = epub.length()
        )

        val viewModel = createViewModel(document)

        assertEquals(UiState.Failure(AppError.FormatNotReadable), viewModel.settled())
    }

    @Test
    fun aFileThatCannotBeOpenedBecomesAFailure() = runTest {
        val broken = File(library, "broken.pdf").apply { writeText("not a pdf") }
        val document = DocumentInfo(
            document = PickedDocument(
                fileName = broken.name,
                extension = "pdf",
                location = broken.absolutePath
            ),
            type = DocumentType.PDF,
            exists = true,
            sizeBytes = broken.length()
        )

        val viewModel = createViewModel(document)

        assertTrue(viewModel.settled() is UiState.Failure)
    }

    @Test
    fun retryReopensTheDocument() = runTest {
        val document = pdfInLibrary(pageCount = 2)
        val repository = FakeLibraryRepository()
        val viewModel = ReaderViewModel(
            location = document.document.location,
            openDocumentUseCase = OpenDocumentUseCase(
                libraryRepository = repository,
                documentWorkspace = workspace,
                dispatcher = openDispatcher
            ),
            settingsRepository = ReaderSettingsRepository(FakeSettingsStore()),
            progressRepository = progress,
            translatePassage = TranslatePassageUseCase(FakeTranslator()),
            translationSettings = TranslationSettingsRepository(FakeSettingsStore(), FakeSecretStore())
        ).also { track(it) }

        assertEquals(UiState.Failure(AppError.DocumentNotFound), viewModel.settled())

        repository.addDocument(document)
        viewModel.retry()

        assertEquals(2, viewModel.settled().dataOrNull?.content?.unitCount)
    }

    @Test
    fun rendersAPageAtTheWidthItIsAskedFor() = runTest {
        val viewModel = createViewModel(pdfInLibrary(pageCount = 1))
        viewModel.settled()

        val page = viewModel.page(index = 0, widthPx = 384)

        assertNotNull(page)
        assertEquals(384, page.width)
    }

    /**
     * Pinching changes the page width continuously. Rounding to a render step is
     * what keeps that from asking for a new render on every frame - two widths a
     * few pixels apart have to come back as the same drawn page.
     */
    @Test
    fun nearbyWidthsShareOneRender() = runTest {
        val viewModel = createViewModel(pdfInLibrary(pageCount = 1))
        viewModel.settled()

        val first = viewModel.page(index = 0, widthPx = 380)
        val second = viewModel.page(index = 0, widthPx = 396)

        assertTrue(first === second)
    }

    /** Scrolling back to a page must not redraw it. */
    @Test
    fun aPageAlreadyDrawnIsServedFromTheCache() = runTest {
        val viewModel = createViewModel(pdfInLibrary(pageCount = 1))
        viewModel.settled()

        val first = viewModel.page(index = 0, widthPx = 384)
        val second = viewModel.page(index = 0, widthPx = 384)

        assertTrue(first === second)
    }

    /**
     * Theme is applied when a page is drawn, not when it is rendered. Changing
     * it must therefore cost nothing: the pages already rendered stay in the
     * cache, which is what keeps the change instant in a long book.
     */
    @Test
    fun changingTheThemeDoesNotDiscardRenderedPages() = runTest {
        val viewModel = createViewModel(pdfInLibrary(pageCount = 1))
        viewModel.settled()

        val before = viewModel.page(index = 0, widthPx = 384)
        viewModel.updateSettings(viewModel.settings.value.copy(theme = ReaderTheme.Dark))
        val after = viewModel.page(index = 0, widthPx = 384)

        assertTrue(before === after)
    }

    /**
     * Reading an outline can mean scanning the whole file, so it is started in
     * the background as the book opens rather than when the panel asks - and
     * never on the thread drawing the pages.
     */
    @Test
    fun theOutlineLoadsWithoutBeingAskedFor() = runTest {
        val viewModel = createViewModel(pdfInLibrary(pageCount = 3, chapters = 2))
        viewModel.settled()

        val outline = viewModel.outline.first { it !is ReaderOutline.Loading }

        assertEquals(
            listOf("Chapter 1", "Chapter 2"),
            (outline as? ReaderOutline.Chapters)?.entries?.map { it.title }
        )
    }

    @Test
    fun aBookWithNoChaptersReportsAnEmptyOutline() = runTest {
        val viewModel = createViewModel(pdfInLibrary(pageCount = 3))
        viewModel.settled()

        val outline = viewModel.outline.first { it !is ReaderOutline.Loading }

        assertEquals(ReaderOutline.Chapters(emptyList()), outline)
    }

    /** A book nobody has opened starts at the beginning, like a new book. */
    @Test
    fun anUnreadBookOpensAtItsFirstPage() = runTest {
        val viewModel = createViewModel(pdfInLibrary(pageCount = 20))
        viewModel.settled()

        assertEquals(0, viewModel.initialPage.first { it != null })
    }

    @Test
    fun aBookOpensWhereItWasLeftOff() = runTest {
        val document = pdfInLibrary(pageCount = 20)
        progress.save(
            location = document.document.location,
            progress = ReadingProgress(pageIndex = 11, pageCount = 20)
        )

        val viewModel = createViewModel(document)
        viewModel.settled()

        assertEquals(11, viewModel.initialPage.first { it != null })
    }

    @Test
    fun whereTheReaderStopsIsWhatIsStored() = runTest {
        val document = pdfInLibrary(pageCount = 20)
        val viewModel = createViewModel(document)
        viewModel.settled()

        viewModel.onPageSettled(pageIndex = 7)

        // Field by field rather than as a whole value: the store also stamps
        // where the book stands against the others, which is its business
        // rather than the reader's.
        val stored = progress.progressFor(document.document.location)

        assertEquals(7, stored?.pageIndex)
        assertEquals(20, stored?.pageCount)
    }

    /**
     * A file can be replaced by a shorter one under the same name, and a page
     * that no longer exists is not a place to open at.
     */
    @Test
    fun aStoredPagePastTheEndOfTheBookOpensAtTheLastPage() = runTest {
        val document = pdfInLibrary(pageCount = 5)
        progress.save(
            location = document.document.location,
            progress = ReadingProgress(pageIndex = 400, pageCount = 500)
        )

        val viewModel = createViewModel(document)
        viewModel.settled()

        assertEquals(4, viewModel.initialPage.first { it != null })
    }

    /** Nothing is stored for a book that never opened - there is no page to store. */
    @Test
    fun aBookThatFailedToOpenRecordsNoPosition() = runTest {
        val viewModel = ReaderViewModel(
            location = "/library/missing.pdf",
            openDocumentUseCase = OpenDocumentUseCase(
                libraryRepository = FakeLibraryRepository(),
                documentWorkspace = workspace,
                dispatcher = openDispatcher
            ),
            settingsRepository = ReaderSettingsRepository(FakeSettingsStore()),
            progressRepository = progress,
            translatePassage = TranslatePassageUseCase(FakeTranslator()),
            translationSettings = TranslationSettingsRepository(FakeSettingsStore(), FakeSecretStore())
        ).also { track(it) }
        viewModel.settled()

        viewModel.onPageSettled(pageIndex = 3)

        assertNull(progress.progressFor("/library/missing.pdf"))
    }

    @Test
    fun settingsChangesAreVisibleThroughTheViewModel() = runTest {
        val viewModel = createViewModel(pdfInLibrary(pageCount = 1))
        viewModel.settled()

        viewModel.updateSettings(viewModel.settings.value.withLargerText())

        assertEquals(110, viewModel.settings.value.textScalePercent)
    }

    /** A resized window invalidates every page drawn for the old one. */
    @Test
    fun aPageIsRedrawnWhenTheWidthChanges() = runTest {
        val viewModel = createViewModel(pdfInLibrary(pageCount = 1))
        viewModel.settled()

        val narrow = viewModel.page(index = 0, widthPx = 384)
        val wide = viewModel.page(index = 0, widthPx = 640)

        assertEquals(384, narrow?.width)
        assertEquals(640, wide?.width)
    }

    @Test
    fun anUnopenedDocumentHasNoPagesToDraw() = runTest {
        val viewModel = ReaderViewModel(
            location = "/library/missing.pdf",
            openDocumentUseCase = OpenDocumentUseCase(
                libraryRepository = FakeLibraryRepository(),
                documentWorkspace = workspace,
                dispatcher = openDispatcher
            ),
            settingsRepository = ReaderSettingsRepository(FakeSettingsStore()),
            progressRepository = progress,
            translatePassage = TranslatePassageUseCase(FakeTranslator()),
            translationSettings = TranslationSettingsRepository(FakeSettingsStore(), FakeSecretStore())
        ).also { track(it) }

        assertNull(viewModel.page(index = 0, widthPx = 384))
    }

    /** Registers a ViewModel so tear-down can cancel its scope. */
    private fun track(viewModel: ReaderViewModel): ReaderViewModel {
        viewModelStore.put("reader-${openedViewModels.size}", viewModel)
        openedViewModels += viewModel
        return viewModel
    }

    /** The state once the document has finished opening, one way or the other. */
    private suspend fun ReaderViewModel.settled(): UiState<ReaderDocument> =
        uiState.first { it !is UiState.Loading }

    private fun createViewModel(document: DocumentInfo) = ReaderViewModel(
        location = document.document.location,
        openDocumentUseCase = OpenDocumentUseCase(
            libraryRepository = FakeLibraryRepository(listOf(document)),
            documentWorkspace = workspace,
            dispatcher = openDispatcher
        ),
        settingsRepository = ReaderSettingsRepository(FakeSettingsStore()),
        progressRepository = progress,
        translatePassage = TranslatePassageUseCase(FakeTranslator()),
        translationSettings = TranslationSettingsRepository(FakeSettingsStore(), FakeSecretStore())
    ).also { track(it) }

    private fun pdfInLibrary(pageCount: Int, chapters: Int = 0): DocumentInfo {
        val file = File(library, "book-${kotlin.random.Random.nextInt()}.pdf")

        PDDocument().use { document ->
            repeat(pageCount) { document.addPage(PDPage(PDRectangle.A4)) }

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
                        }
                    )
                }
            }

            document.save(file)
        }

        return DocumentInfo(
            document = PickedDocument(
                fileName = file.name,
                extension = "pdf",
                location = file.absolutePath
            ),
            type = DocumentType.PDF,
            exists = true,
            sizeBytes = file.length()
        )
    }

    private companion object {
        /** Generous: the work is a few small files, not a real book. */
        const val SETTLE_TIMEOUT_MILLIS = 10_000L
    }

    private class FakeLibraryRepository(
        initialDocuments: List<DocumentInfo> = emptyList()
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
