package io.lukia

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoutesTest {

    @Test
    fun createRouteEncodesLocationAsPathSegment() {
        val location = "/storage/emulated/0/Download/book.pdf"

        val route = Routes.Detail.createRoute(location)

        assertTrue(route.startsWith("detail/"))
        assertTrue("/" !in route.substringAfter("detail/"))
    }

    @Test
    fun decodeLocationReversesCreateRouteForPlainPath() {
        val location = "/storage/emulated/0/Download/book.pdf"

        val token = Routes.Detail.createRoute(location).removePrefix("detail/")

        assertEquals(location, Routes.Detail.decodeLocation(token))
    }

    @Test
    fun decodeLocationReversesCreateRouteForContentUri() {
        val location = "content://com.android.providers.downloads.documents/document/42"

        val token = Routes.Detail.createRoute(location).removePrefix("detail/")

        assertEquals(location, Routes.Detail.decodeLocation(token))
    }

    @Test
    fun decodeLocationReversesCreateRouteForWindowsPath() {
        val location = "C:\\Users\\test\\Documents\\book.epub"

        val token = Routes.Detail.createRoute(location).removePrefix("detail/")

        assertEquals(location, Routes.Detail.decodeLocation(token))
    }
}
