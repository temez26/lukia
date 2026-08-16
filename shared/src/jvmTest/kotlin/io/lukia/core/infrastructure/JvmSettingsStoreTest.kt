package io.lukia.core.infrastructure

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JvmSettingsStoreTest {

    private val directory = File(
        System.getProperty("java.io.tmpdir"),
        "lukia-settings-test-${kotlin.random.Random.nextInt(until = Int.MAX_VALUE)}"
    )
    private val file = File(directory, "settings.properties")

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun anUnwrittenKeyIsAbsent() {
        assertNull(JvmSettingsStore(file).getString("reader.theme"))
    }

    @Test
    fun aValueIsReadBackWithinTheSession() {
        val store = JvmSettingsStore(file)

        store.putString("reader.theme", "Sepia")

        assertEquals("Sepia", store.getString("reader.theme"))
    }

    /** The point of writing to a file: the value is there the next time the app runs. */
    @Test
    fun aValueSurvivesANewStore() {
        JvmSettingsStore(file).putString("reader.theme", "Dark")

        assertEquals("Dark", JvmSettingsStore(file).getString("reader.theme"))
    }

    @Test
    fun writingCreatesTheDirectory() {
        JvmSettingsStore(file).putString("reader.theme", "Dark")

        assertEquals(true, file.isFile)
    }

    /** A settings file that has been damaged must not stop the app from opening. */
    @Test
    fun anUnreadableFileFallsBackToNothingStored() {
        directory.mkdirs()
        file.writeBytes(byteArrayOf(0, 1, 2))

        assertNull(JvmSettingsStore(file).getString("reader.theme"))
    }
}
