package io.lukia

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/**
 * A test of something that launches into `viewModelScope`.
 *
 * That scope runs on the main dispatcher, which does not exist off a device, so
 * every such test has to install one and put it back afterwards. Inherited
 * rather than repeated: a class that forgets the reset leaves the replacement
 * installed for whatever runs next, and the failure surfaces in a different
 * test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class MainDispatcherTest {

    @BeforeTest
    fun installMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun restoreMainDispatcher() {
        Dispatchers.resetMain()
    }
}
