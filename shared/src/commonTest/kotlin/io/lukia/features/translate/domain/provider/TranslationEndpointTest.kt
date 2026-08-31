package io.lukia.features.translate.domain.provider

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What travels to a translator is a sentence out of the book somebody is
 * reading and the title of that book. Over a home network in the clear that is
 * between them and their own computer; across the internet in the clear it is
 * between them and everyone on the way.
 */
class TranslationEndpointTest {

    @Test
    fun httpsIsAllowedAnywhere() {
        assertTrue(isAllowedTranslationEndpoint("https://api.anthropic.com/v1"))
        assertTrue(isAllowedTranslationEndpoint("https://api.openai.com/v1"))
        assertTrue(isAllowedTranslationEndpoint("https://models.example.com:8443/v1"))
    }

    @Test
    fun cleartextToThisMachineIsAllowed() {
        assertTrue(isAllowedTranslationEndpoint("http://localhost:1234/v1"))
        assertTrue(isAllowedTranslationEndpoint("http://127.0.0.1:1234/v1"))
        assertTrue(isAllowedTranslationEndpoint("http://[::1]:1234/v1"))
    }

    @Test
    fun cleartextToTheReadersOwnNetworkIsAllowed() {
        assertTrue(isAllowedTranslationEndpoint("http://192.168.1.50:1234/v1"))
        assertTrue(isAllowedTranslationEndpoint("http://10.0.0.7:8080/v1"))
        assertTrue(isAllowedTranslationEndpoint("http://172.16.4.4/v1"))
        assertTrue(isAllowedTranslationEndpoint("http://172.31.255.255/v1"))
        // What an Android emulator reaches the machine hosting it on.
        assertTrue(isAllowedTranslationEndpoint("http://10.0.2.2:1234/v1"))
        assertTrue(isAllowedTranslationEndpoint("http://studio.local:1234/v1"))
    }

    @Test
    fun cleartextToThePublicInternetIsRefused() {
        assertFalse(isAllowedTranslationEndpoint("http://api.openai.com/v1"))
        assertFalse(isAllowedTranslationEndpoint("http://8.8.8.8/v1"))
        assertFalse(isAllowedTranslationEndpoint("http://models.example.com:1234/v1"))
    }

    /**
     * 172 is only private from 16 to 31, and 192 only at 168. Addresses either
     * side of those are ordinary public ones.
     */
    @Test
    fun theEdgesOfThePrivateRangesAreNotInsideThem() {
        assertFalse(isAllowedTranslationEndpoint("http://172.15.0.1/v1"))
        assertFalse(isAllowedTranslationEndpoint("http://172.32.0.1/v1"))
        assertFalse(isAllowedTranslationEndpoint("http://192.167.1.1/v1"))
        assertFalse(isAllowedTranslationEndpoint("http://11.0.0.1/v1"))
    }

    /** A host that merely reads like a private address is not one. */
    @Test
    fun aNameThatLooksLikeAPrivateAddressIsStillAName() {
        assertFalse(isAllowedTranslationEndpoint("http://192.168.1.50.example.com/v1"))
        assertFalse(isAllowedTranslationEndpoint("http://localhost.example.com/v1"))
        assertFalse(isAllowedTranslationEndpoint("http://999.999.999.999/v1"))
    }

    /** Credentials in the authority must not be mistaken for the host. */
    @Test
    fun aUserInfoPrefixDoesNotDecideTheHost() {
        assertFalse(isAllowedTranslationEndpoint("http://localhost@evil.example.com/v1"))
        assertTrue(isAllowedTranslationEndpoint("http://user@192.168.1.50:1234/v1"))
    }

    @Test
    fun somethingThatIsNotAnAddressIsRefused() {
        assertFalse(isAllowedTranslationEndpoint(""))
        assertFalse(isAllowedTranslationEndpoint("localhost:1234"))
        assertFalse(isAllowedTranslationEndpoint("ftp://192.168.1.50/v1"))
        assertFalse(isAllowedTranslationEndpoint("file:///etc/passwd"))
    }

    @Test
    fun theSchemeIsReadWhateverItsCase() {
        assertTrue(isAllowedTranslationEndpoint("HTTPS://api.anthropic.com/v1"))
        assertTrue(isAllowedTranslationEndpoint("  https://api.anthropic.com/v1  "))
        assertTrue(isAllowedTranslationEndpoint("HTTP://LOCALHOST:1234/v1"))
    }
}
