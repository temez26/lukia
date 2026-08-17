package io.lukia.core.infrastructure

import io.lukia.core.domain.SecretStore
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Secrets in the system keychain, where the platform keeps this kind of thing.
 *
 * Encrypted at rest by the system, tied to this app, and not readable by
 * anything else - which is the whole reason for keeping a key somewhere other
 * than user defaults, where it would sit in a plist beside the reading theme.
 *
 * Accessible after the first unlock and marked as belonging to this device
 * only: a book is read on a train with the screen already on, so demanding the
 * device be unlocked at the moment of each request would fail translations for
 * no gain - but a key restored onto somebody else's phone from a backup is a
 * key that has left its owner, so it does not travel.
 *
 * The queries are built as Core Foundation dictionaries rather than as Kotlin
 * maps bridged across. The keys here are `CFStringRef` constants, and handing
 * those to a bridged map relies on a conversion that is not the one it looks
 * like - this way what goes to the keychain is exactly what was assembled.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IOSSecretStore : SecretStore {

    override fun getSecret(key: String): String? = memScoped {
        val query = query(key)
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)

        val found = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, found.ptr)
        CFRelease(query)

        if (status != errSecSuccess) return@memScoped null

        // CFBridgingRelease takes ownership of what the search returned, so the
        // data is freed with the Kotlin object rather than leaked.
        val data = CFBridgingRelease(found.value) as? NSData ?: return@memScoped null

        NSString.create(data = data, encoding = NSUTF8StringEncoding) as String?
    }

    /**
     * Removed and re-added rather than updated in place. An update is a second
     * code path existing only to avoid deleting an item that is about to be
     * replaced anyway, and every one of these writes is somebody typing a key
     * one character at a time.
     */
    override fun putSecret(key: String, value: String) {
        removeSecret(key)

        if (value.isBlank()) return

        val encoded = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val data = CFBridgingRetain(encoded)

        val entry = query(key)
        CFDictionaryAddValue(entry, kSecValueData, data)
        CFDictionaryAddValue(
            entry,
            kSecAttrAccessible,
            kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        )

        SecItemAdd(entry, null)

        // The dictionary retained the data when it took it, so this hands back
        // the reference taken above rather than the keychain's own.
        CFRelease(data)
        CFRelease(entry)
    }

    override fun removeSecret(key: String) {
        val query = query(key)

        SecItemDelete(query)
        CFRelease(query)
    }

    /** The item this key names. The caller owns what comes back and releases it. */
    private fun query(key: String): CFMutableDictionaryRef? {
        val query = CFDictionaryCreateMutable(
            null,
            0,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr
        )

        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        query.add(kSecAttrService, SERVICE)
        query.add(kSecAttrAccount, key)

        return query
    }

    /** Adds a Kotlin string under a Core Foundation key, without leaking it. */
    private fun CFMutableDictionaryRef?.add(key: CPointer<*>?, value: String) {
        val bridged: CFTypeRef? = CFBridgingRetain(value as NSString)

        CFDictionaryAddValue(this, key, bridged)
        CFRelease(bridged)
    }

    private companion object {
        const val SERVICE = "io.lukia.secrets"
    }
}
