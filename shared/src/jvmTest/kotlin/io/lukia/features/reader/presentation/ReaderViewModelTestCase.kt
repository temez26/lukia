package io.lukia.features.reader.presentation

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import io.lukia.core.domain.document.CoverImage
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.PickedDocument
import io.lukia.core.domain.reading.FakePdfLayoutRepository
import io.lukia.core.domain.reading.FakeReadingSessionRepository
import io.lukia.core.domain.storage.FakeSecretStore
import io.lukia.core.domain.storage.FakeSettingsStore
import io.lukia.core.infrastructure.JvmDocumentWorkspace
import io.lukia.core.infrastructure.shelve
import io.lukia.core.infrastructure.testDatabase
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.dataOrNull
import io.lukia.features.library.domain.LibraryRepository
import io.lukia.features.reader.application.OpenDocumentUseCase
import io.lukia.features.reader.domain.ReaderDocument
import io.lukia.features.reader.domain.outline.ReaderOutline
import io.lukia.features.reader.infrastructure.ReaderSettingsRepository
import io.lukia.features.reader.infrastructure.SqlReadingProgressRepository
import io.lukia.features.reader.presentation.translation.ReaderTranslationsFactory
import io.lukia.features.translate.application.LookUpWordUseCase
import io.lukia.features.translate.application.TranslatePassageUseCase
import io.lukia.features.translate.domain.Translator
import io.lukia.features.translate.domain.lookup.WordDictionary
import io.lukia.features.translate.domain.lookup.WordEntry
import io.lukia.features.translate.domain.lookup.WordMeaning
import io.lukia.features.translate.domain.provider.FakeTranslator
import io.lukia.core.domain.text.PassageUnit
import io.lukia.features.translate.domain.settings.TranslationGestures
import io.lukia.features.translate.infrastructure.TranslationSettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
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
    protected val database = testDatabase()

    protected val progress = SqlReadingProgressRepository(
        database = database,
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

    /**
     * The scope job of each ViewModel, taken while it is still alive.
     *
     * Held separately because it is the one thing tear-down needs *after*
     * clearing the store, and asking a cleared ViewModel for its scope hands
     * back a fresh one that is already finished - which would be a wait that
     * always returns immediately and never waited for anything.
     */
    private val viewModelJobs = mutableListOf<Job>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    /**
     * Lets each ViewModel finish before taking its world away.
     *
     * Opening a book starts two pieces of real work - the copy and then the
     * outline - and cancelling a coroutine inside a blocking read does not stop
     * it: it still has to resume, on a main dispatcher [resetMain] would by then
     * have removed.
     *
     * Clearing the store only *starts* the cancelling, so the scopes are joined
     * rather than given a moment: a cancelled coroutine still has to reach its
     * next suspension point, and one resuming while [resetMain] runs throws into
     * whichever test starts next. Joining is the moment that is over, and it is
     * the same moment on a loaded machine as on an idle one.
     *
     * The open document is closed last for a related reason: its file handle
     * has to be given back before the directory underneath it goes, and
     * nothing may still be reading through it when it is.
     */
    @AfterTest
    fun tearDown() {
        runBlocking {
            withTimeoutOrNull(SETTLE_TIMEOUT_MILLIS) {
                openedViewModels.forEach { it.settled() }
            }
        }

        viewModelStore.clear()

        runBlocking {
            withTimeoutOrNull(SETTLE_TIMEOUT_MILLIS) { viewModelJobs.forEach { it.join() } }
        }

        openedViewModels.forEach { it.uiState.value.dataOrNull?.close() }

        Dispatchers.resetMain()
        root.deleteRecursively()
    }

    protected fun track(viewModel: ReaderViewModel): ReaderViewModel {
        viewModelStore.put("reader-${openedViewModels.size}", viewModel)
        openedViewModels += viewModel
        viewModelJobs += viewModel.viewModelScope.coroutineContext.job
        return viewModel
    }

    /**
     * The state once the document has finished opening, one way or the other.
     *
     * A book that opened is waited for twice, because opening publishes the
     * state part-way through the work: `uiState` leaves `Loading`, and the same
     * coroutine goes on to settle the layout choice, read the stored position
     * and then the outline. The app never sees that gap - its main dispatcher is
     * one thread. Here the tail resumes on whichever pool thread finished the
     * copy while the test resumes on its own, so a wait ending at the first
     * signal would read [ReaderViewModel.layoutOffered] or
     * [ReaderViewModel.textExtraction] mid-write.
     *
     * The outline is the last of it and is reached from the coroutine that set
     * the rest, so waiting for it waits for all of it. Anything the opening
     * learns to set after the outline needs this wait extended.
     */
    protected suspend fun ReaderViewModel.settled(): UiState<ReaderDocument> {
        val state = uiState.first { it !is UiState.Loading }

        // Only a book that opened goes on to read an outline.
        if (state is UiState.Success) outline.first { it !is ReaderOutline.Loading }

        return state
    }

    protected fun createViewModel(
        document: DocumentInfo,
        /** Supplied where a test cares what new books are set to open as. */
        settings: ReaderSettingsRepository = ReaderSettingsRepository(FakeSettingsStore()),
        /** Supplied where a test cares what becomes of a passage it asks about. */
        translations: ReaderTranslationsFactory = testTranslations(),
    ) = ReaderViewModel(
        location = document.document.location,
        openDocumentUseCase = OpenDocumentUseCase(
            catalogue = FakeLibraryRepository(listOf(document)),
            documentWorkspace = workspace,
            dispatcher = openDispatcher,
        ),
        settingsRepository = settings,
        reading = BookReadingFactory(progress, FakeReadingSessionRepository()),
        layoutRepository = layouts,
        translations = translations,
        // Inline, because the outline is read beside the open book rather than
        // as part of it: on a dispatcher of its own it is work no test can wait
        // for, and a reopened book leaves two of them running at once.
        outlineDispatcher = openDispatcher,
        sentenceDispatcher = sentenceDispatcher,
    ).also { track(it) }

    /**
     * Where a page's words are gathered. Inline by default for the reason the
     * outline is; a test about *where* that work runs replaces it.
     */
    protected open val sentenceDispatcher: CoroutineDispatcher = Dispatchers.Unconfined

    /**
     * A translator wired the way the app wires the real one.
     *
     * [translator] is the one thing a test about translating wants to replace:
     * what becomes of a passage - answered, failed, still arriving - is decided
     * entirely by what is behind this.
     */
    protected fun testTranslations(
        translator: Translator = FakeTranslator(),
        /**
         * What the reader has set a tap and a hold to.
         *
         * Turned on here rather than taken from `TranslationGestures.Default`,
         * which is now every gesture off - a book that translates nothing until
         * its reader asks. Every test that reaches this is about what happens
         * once they have, so this is the reader who has.
         */
        gestures: TranslationGestures = TranslationGestures.of(
            tap = PassageUnit.Sentence,
            longPress = PassageUnit.Word,
            wordDetails = true,
        ),
    ) = ReaderTranslationsFactory(
        TranslatePassageUseCase(translator),
        LookUpWordUseCase(WordDictionary { word, _ -> WordEntry(word, listOf(WordMeaning(null, listOf("a sense")))) }),
        TranslationSettingsRepository(FakeSettingsStore(), FakeSecretStore()).apply {
            update(settings.value.withGestures(gestures))
        },
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

        // On the shelf as well as on disk, which is what the name says: a
        // position is keyed to the book it is about - see `ReadingPosition.sq` -
        // so a book the library has never heard of has nowhere to store one.
        database.shelve(file.absolutePath)

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
        /**
         * A backstop against a wait that can never end, rather than a duration
         * anything is expected to take: the work is a few small files, not a
         * real book.
         */
        const val SETTLE_TIMEOUT_MILLIS = 10_000L
    }

    protected class FakeLibraryRepository(
        initialDocuments: List<DocumentInfo> = emptyList(),
    ) : LibraryRepository {
        private val documents = initialDocuments.toMutableList()

        override suspend fun addDocument(document: DocumentInfo) {
            documents.add(document)
        }

        override suspend fun getDocuments(): List<DocumentInfo> =
            documents.map { it.copy(coverImage = null) }

        override suspend fun getDocument(location: String): DocumentInfo? =
            documents.firstOrNull { it.document.location == location }

        /** Read by location, exactly as the real store reads it. */
        override suspend fun coverFor(location: String): CoverImage? =
            getDocument(location)?.coverImage

        /** Answered the expensive way; nothing in a test cares what it costs. */
        override suspend fun knownLocations(): Set<String> =
            getDocuments().mapTo(mutableSetOf()) { it.document.location }

        override suspend fun setFavorite(location: String, isFavorite: Boolean) = Unit

        override suspend fun removeDocument(location: String) {
            documents.removeAll { it.document.location == location }
        }
    }
}
