package io.lukia.core.infrastructure.diagnostics

import io.lukia.core.domain.diagnostics.CrashStore
import io.lukia.core.domain.diagnostics.DiagnosticArea
import io.lukia.core.domain.diagnostics.FakeCrashStore
import io.lukia.core.domain.diagnostics.warn
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class CrashRecorderTest {

    private val originalHandler = Thread.getDefaultUncaughtExceptionHandler()

    @AfterTest
    fun restoreTheHandler() {
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
    }

    /**
     * The whole feature turns on this: installing has to actually replace the
     * handler, or a crash goes exactly where it went before and the file is
     * never written.
     */
    @Test
    fun installingPutsAHookInPlace() {
        val before = Thread.getDefaultUncaughtExceptionHandler()

        recorder(FakeCrashStore()).install()

        assertNotSame(before, Thread.getDefaultUncaughtExceptionHandler())
    }

    /**
     * The handler that was already there is what shows Android's crash dialog
     * and hands the crash to Play, which is the crash reporting this app relies
     * on rather than shipping an SDK. Replacing it outright would trade the
     * reporting that works for a file on the device.
     */
    @Test
    fun theHandlerThatWasAlreadyThereStillRuns() {
        var reachedTheOriginal = false

        Thread.setDefaultUncaughtExceptionHandler { _, _ -> reachedTheOriginal = true }
        recorder(FakeCrashStore()).install()

        Thread.getDefaultUncaughtExceptionHandler()
            ?.uncaughtException(Thread.currentThread(), IllegalStateException("boom"))

        assertTrue(reachedTheOriginal, "the previous handler was replaced rather than chained")
    }

    @Test
    fun aCrashIsWrittenDownAsItHappens() {
        val store = FakeCrashStore()

        recorder(store).install()
        crash(IllegalStateException("boom"))

        val written = store.read()

        assertTrue(written != null && written.contains("IllegalStateException"), "$written")
    }

    /**
     * The consent split, and it needs no code: the buffer is empty unless the
     * reader turned diagnostics on, so a crash from somebody who never did
     * carries the trace and nothing about their afternoon.
     */
    @Test
    fun aCrashCarriesNoRunUpWhenNothingWasBeingRecorded() {
        val store = FakeCrashStore()
        val buffer = DiagnosticBuffer()

        recorder(store, buffer).install()
        crash(IllegalStateException("boom"))

        val written = store.read().orEmpty()

        assertTrue(written.contains("Nothing was recorded before it"), written)
    }

    @Test
    fun aCrashCarriesTheRunUpWhenThereWasOne() {
        val store = FakeCrashStore()
        val buffer = DiagnosticBuffer()

        RecordingDiagnostics(buffer).warn(
            area = DiagnosticArea.Reading,
            event = "pdf-pages-unreadable",
            detail = "12 of 412",
        )

        recorder(store, buffer).install()
        crash(IllegalStateException("boom"))

        assertTrue(store.read().orEmpty().contains("pdf-pages-unreadable"), store.read().orEmpty())
    }

    /**
     * A failure while writing a note about a failure must not replace the one
     * worth reading, so the hook has to survive its own store throwing.
     */
    @Test
    fun aStoreThatFailsDoesNotTakeTheCrashWithIt() {
        var reachedTheOriginal = false

        Thread.setDefaultUncaughtExceptionHandler { _, _ -> reachedTheOriginal = true }

        val failing = object : CrashStore {
            override fun write(report: String) = error("the disk is full")
            override fun read(): String? = null
            override fun discard() = Unit
        }

        recorder(failing).install()
        crash(IllegalStateException("boom"))

        assertTrue(reachedTheOriginal, "a failing store stopped the real handler running")
    }

    @Test
    fun whatWasLeftBehindIsReadBackAndCanBeDiscarded() {
        val recorder = recorder(FakeCrashStore("an earlier crash"))

        assertEquals("an earlier crash", recorder.lastCrash())

        recorder.discard()

        assertNull(recorder.lastCrash())
    }

    @Test
    fun aCleanCloseLeavesNothingToFind() {
        assertNull(recorder(FakeCrashStore()).lastCrash())
    }

    /** A crash report never carries a failure's own words - only its type and lines. */
    @Test
    fun theMessageOfTheFailureIsNotWrittenDown() {
        val store = FakeCrashStore()

        recorder(store).install()
        crash(IllegalStateException("/Users/anna-liisa/Documents/Books/Anna-Liisa.epub"))

        val written = store.read().orEmpty()

        assertFalse(written.contains("anna-liisa"), written)
        assertTrue(written.contains("IllegalStateException"), written)
    }

    private fun crash(failure: Throwable) =
        Thread.getDefaultUncaughtExceptionHandler()
            ?.uncaughtException(Thread.currentThread(), failure)

    private fun recorder(
        store: CrashStore,
        buffer: DiagnosticBuffer = DiagnosticBuffer(),
    ) = CrashRecorder(
        store = store,
        buffer = buffer,
        platformName = "Java 21",
        now = { Instant.fromEpochMilliseconds(0) },
    )
}
