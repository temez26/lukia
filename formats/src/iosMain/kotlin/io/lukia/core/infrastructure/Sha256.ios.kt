package io.lukia.core.infrastructure

actual class Sha256 actual constructor() {
    private val portable = PortableSha256()

    actual fun update(bytes: ByteArray, offset: Int, length: Int) = portable.update(bytes, offset, length)

    actual fun digest(): ByteArray = portable.digest()
}
