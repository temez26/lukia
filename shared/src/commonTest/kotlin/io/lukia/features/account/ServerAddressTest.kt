package io.lukia.features.account

import io.lukia.features.account.domain.AccountFailure
import io.lukia.features.account.domain.AccountProblem
import io.lukia.features.account.domain.serverAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ServerAddressTest {

    @Test
    fun anAddressOnTheReadersOwnNetworkIsPlainHttpWhenNoSchemeIsTyped() {
        assertEquals("http://192.168.1.10:8080", serverAddress("192.168.1.10:8080"))
        assertEquals("http://localhost:8080", serverAddress(" localhost:8080/ "))
    }

    @Test
    fun anyOtherAddressIsHttpsWhenNoSchemeIsTyped() {
        assertEquals("https://books.example.com", serverAddress("books.example.com"))
    }

    @Test
    fun aTypedSchemeIsKept() {
        assertEquals("https://192.168.1.10:8443", serverAddress("https://192.168.1.10:8443"))
        assertEquals("https://books.example.com/lukia", serverAddress("https://books.example.com/lukia/"))
    }

    @Test
    fun plainHttpAcrossTheInternetIsRefused() {
        assertEquals(AccountProblem.InsecureAddress, problemWith("http://books.example.com"))
        assertEquals(AccountProblem.InsecureAddress, problemWith("http://8.8.8.8:8080"))
    }

    @Test
    fun whatIsNotAnAddressIsSaidToBeSo() {
        assertEquals(AccountProblem.InvalidAddress, problemWith(""))
        assertEquals(AccountProblem.InvalidAddress, problemWith("my server"))
        assertEquals(AccountProblem.InvalidAddress, problemWith("ftp://books.example.com"))
        assertEquals(AccountProblem.InvalidAddress, problemWith("https://"))
    }

    private fun problemWith(typed: String) = assertFailsWith<AccountFailure> { serverAddress(typed) }.problem
}
