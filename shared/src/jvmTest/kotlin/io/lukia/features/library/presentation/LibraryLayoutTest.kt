package io.lukia.features.library.presentation

import io.lukia.MainDispatcherTest
import io.lukia.core.domain.storage.FakeSettingsStore
import io.lukia.features.library.domain.LibraryLayout
import io.lukia.features.library.infrastructure.LibrarySettingsRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How the shelf is laid out, and that the choice outlives the session.
 */
class LibraryLayoutTest : MainDispatcherTest() {

    /** A library is browsed by its covers until it is too big to browse. */
    @Test
    fun theShelfOpensAsAGrid() = runTest {
        val viewModel = createViewModel(FakeLibraryRepository())

        assertEquals(LibraryLayout.Grid, viewModel.layout.value)
    }

    @Test
    fun theChosenLayoutIsReadBack() = runTest {
        val store = FakeSettingsStore()
        val settings = LibrarySettingsRepository(store)

        settings.setLayout(LibraryLayout.List)

        assertEquals(LibraryLayout.List, LibrarySettingsRepository(store).layout.value)
    }

    /**
     * A value written by an older build, or edited by hand into nonsense, falls
     * back to the default rather than taking the shelf down with it.
     */
    @Test
    fun aLayoutThatCannotBeReadBackOpensAsAGrid() = runTest {
        val store = FakeSettingsStore()
        store.putString("library.layout", "Carousel")

        assertEquals(LibraryLayout.Grid, LibrarySettingsRepository(store).layout.value)
    }
}
