package io.lukia.core.domain.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What travels to a translator is a sentence out of the book somebody is
 * reading and the title of that book. Over a home network in the clear that is
 * between them and their own computer; across the internet in the clear it is
 * between them and everyone on the way.
 */
class EndpointTest {

    @Test
    fun httpsIsAllowedAnywhere() {
        assertTrue(isAllowedEndpoint("https://api.anthropic.com/v1"))
        assertTrue(isAllowedEndpoint("https://api.openai.com/v1"))
        assertTrue(isAllowedEndpoint("https://models.example.com:8443/v1"))
    }

    @Test
    fun cleartextToThisMachineIsAllowed() {
        assertTrue(isAllowedEndpoint("http://localhost:1234/v1"))
        assertTrue(isAllowedEndpoint("http://127.0.0.1:1234/v1"))
        assertTrue(isAllowedEndpoint("http://[::1]:1234/v1"))
    }

    @Test
    fun cleartextToTheReadersOwnNetworkIsAllowed() {
        assertTrue(isAllowedEndpoint("http://192.168.1.50:1234/v1"))
        assertTrue(isAllowedEndpoint("http://10.0.0.7:8080/v1"))
        assertTrue(isAllowedEndpoint("http://172.16.4.4/v1"))
        assertTrue(isAllowedEndpoint("http://172.31.255.255/v1"))
        // What an Android emulator reaches the machine hosting it on.
        assertTrue(isAllowedEndpoint("http://10.0.2.2:1234/v1"))
        assertTrue(isAllowedEndpoint("http://studio.local:1234/v1"))
    }

    @Test
    fun cleartextToThePublicInternetIsRefused() {
        assertFalse(isAllowedEndpoint("http://api.openai.com/v1"))
        assertFalse(isAllowedEndpoint("http://8.8.8.8/v1"))
        assertFalse(isAllowedEndpoint("http://models.example.com:1234/v1"))
    }

    /**
     * 172 is only private from 16 to 31, and 192 only at 168. Addresses either
     * side of those are ordinary public ones.
     */
    @Test
    fun theEdgesOfThePrivateRangesAreNotInsideThem() {
        assertFalse(isAllowedEndpoint("http://172.15.0.1/v1"))
        assertFalse(isAllowedEndpoint("http://172.32.0.1/v1"))
        assertFalse(isAllowedEndpoint("http://192.167.1.1/v1"))
        assertFalse(isAllowedEndpoint("http://11.0.0.1/v1"))
    }

    /** A host that merely reads like a private address is not one. */
    @Test
    fun aNameThatLooksLikeAPrivateAddressIsStillAName() {
        assertFalse(isAllowedEndpoint("http://192.168.1.50.example.com/v1"))
        assertFalse(isAllowedEndpoint("http://localhost.example.com/v1"))
        assertFalse(isAllowedEndpoint("http://999.999.999.999/v1"))
    }

    /** Credentials in the authority must not be mistaken for the host. */
    @Test
    fun aUserInfoPrefixDoesNotDecideTheHost() {
        assertFalse(isAllowedEndpoint("http://localhost@evil.example.com/v1"))
        assertTrue(isAllowedEndpoint("http://user@192.168.1.50:1234/v1"))
    }

    @Test
    fun somethingThatIsNotAnAddressIsRefused() {
        assertFalse(isAllowedEndpoint(""))
        assertFalse(isAllowedEndpoint("localhost:1234"))
        assertFalse(isAllowedEndpoint("ftp://192.168.1.50/v1"))
        assertFalse(isAllowedEndpoint("file:///etc/passwd"))
    }

    @Test
    fun theSchemeIsReadWhateverItsCase() {
        assertTrue(isAllowedEndpoint("HTTPS://api.anthropic.com/v1"))
        assertTrue(isAllowedEndpoint("  https://api.anthropic.com/v1  "))
        assertTrue(isAllowedEndpoint("HTTP://LOCALHOST:1234/v1"))
        // Mixed case is the form that used to be recognised as http and then
        // not stripped as http, leaving the scheme itself to be read as a host.
        assertTrue(isAllowedEndpoint("Http://localhost:1234/v1"))
        assertFalse(isAllowedEndpoint("Http://evil.example.com/v1"))
    }

    /**
     * The host is what follows the last `@`, not the first. Everything before
     * it is credentials, and a URL carrying two of them is somebody trying to
     * have a private address read where a public one will be reached.
     */
    @Test
    fun theHostIsTakenFromTheLastUserInfoSeparator() {
        assertFalse(isAllowedEndpoint("http://user@127.0.0.1@evil.example.com/v1"))
        assertFalse(isAllowedEndpoint("http://a@b@8.8.8.8/v1"))
        assertTrue(isAllowedEndpoint("http://a@b@127.0.0.1/v1"))
    }

    @Test
    fun cleartextToThisMachineOverIpv6IsAllowed() {
        assertTrue(isAllowedEndpoint("http://[::1]:1234/v1"))
        assertTrue(isAllowedEndpoint("http://[0:0:0:0:0:0:0:1]:1234/v1"))
        // An IPv4 address carried inside an IPv6 one reaches what it says.
        assertTrue(isAllowedEndpoint("http://[::ffff:127.0.0.1]:1234/v1"))
        assertTrue(isAllowedEndpoint("http://[::ffff:192.168.1.50]/v1"))
    }

    @Test
    fun cleartextToTheReadersOwnNetworkOverIpv6IsAllowed() {
        // Unique local, which is IPv6's answer to 10/8 and 192.168/16.
        assertTrue(isAllowedEndpoint("http://[fd00::1]:1234/v1"))
        assertTrue(isAllowedEndpoint("http://[fc00::1]:1234/v1"))
        // Link-local, including the interface it is scoped to.
        assertTrue(isAllowedEndpoint("http://[fe80::1]:1234/v1"))
        assertTrue(isAllowedEndpoint("http://[fe80::1%25en0]:1234/v1"))
    }

    @Test
    fun cleartextToAPublicIpv6AddressIsRefused() {
        assertFalse(isAllowedEndpoint("http://[2001:4860:4860::8888]/v1"))
        assertFalse(isAllowedEndpoint("http://[::ffff:8.8.8.8]/v1"))
        assertFalse(isAllowedEndpoint("http://[::]/v1"))
    }

    /**
     * Carrier-grade NAT looks private and is not the reader's: it is the space
     * an internet provider puts between its own customers.
     */
    @Test
    fun cleartextToCarrierNatIsRefused() {
        assertFalse(isAllowedEndpoint("http://100.64.0.1/v1"))
        assertFalse(isAllowedEndpoint("http://100.127.255.255/v1"))
    }
}
