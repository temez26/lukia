package io.lukia.features.reader.infrastructure

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.CoreGraphics.CGPDFArrayGetInteger
import platform.CoreGraphics.CGPDFArrayGetName
import platform.CoreGraphics.CGPDFArrayGetNumber
import platform.CoreGraphics.CGPDFArrayGetObject
import platform.CoreGraphics.CGPDFArrayGetStream
import platform.CoreGraphics.CGPDFArrayGetString
import platform.CoreGraphics.CGPDFArrayRef
import platform.CoreGraphics.CGPDFArrayRefVar
import platform.CoreGraphics.CGPDFBooleanVar
import platform.CoreGraphics.CGPDFDictionaryGetArray
import platform.CoreGraphics.CGPDFDictionaryGetBoolean
import platform.CoreGraphics.CGPDFDictionaryGetDictionary
import platform.CoreGraphics.CGPDFDictionaryGetInteger
import platform.CoreGraphics.CGPDFDictionaryGetName
import platform.CoreGraphics.CGPDFDictionaryGetObject
import platform.CoreGraphics.CGPDFDictionaryGetStream
import platform.CoreGraphics.CGPDFDictionaryRef
import platform.CoreGraphics.CGPDFDictionaryRefVar
import platform.CoreGraphics.CGPDFIntegerVar
import platform.CoreGraphics.CGPDFObjectGetValue
import platform.CoreGraphics.CGPDFObjectRef
import platform.CoreGraphics.CGPDFObjectRefVar
import platform.CoreGraphics.CGPDFStreamRef
import platform.CoreGraphics.CGPDFStreamRefVar
import platform.CoreGraphics.CGPDFStringGetBytePtr
import platform.CoreGraphics.CGPDFStringGetLength
import platform.CoreGraphics.CGPDFStringRef
import platform.CoreGraphics.CGPDFStringRefVar
import platform.CoreGraphics.kCGPDFObjectTypeArray
import platform.CoreGraphics.kCGPDFObjectTypeName
import platform.CoreGraphics.kCGPDFObjectTypeStream

/**
 * Reading a PDF's own objects through CoreGraphics.
 *
 * CoreGraphics answers every question about a PDF object by writing into an out
 * parameter and returning whether it managed to, which would put four lines of
 * `memScoped` around every field read. These turn each of them into the
 * nullable value the rest of the reader works in, and are the only place that
 * has to know about that convention.
 *
 * Only the accessors the picture walk actually asks for are here. A PDF object
 * model is a large thing to write, and none of the rest of it is wanted: what
 * follows needs an XObject's type, its shape and its colour, and nothing else
 * about the file.
 */

@OptIn(ExperimentalForeignApi::class)
internal fun CGPDFDictionaryRef.pdfName(key: String): String? = memScoped {
    val value = alloc<CPointerVar<ByteVar>>()

    if (CGPDFDictionaryGetName(this@pdfName, key, value.ptr)) value.value?.toKString() else null
}

@OptIn(ExperimentalForeignApi::class)
internal fun CGPDFDictionaryRef.pdfFlag(key: String): Boolean? = memScoped {
    val value = alloc<CGPDFBooleanVar>()

    if (CGPDFDictionaryGetBoolean(this@pdfFlag, key, value.ptr)) value.value.toInt() != 0 else null
}

@OptIn(ExperimentalForeignApi::class)
internal fun CGPDFDictionaryRef.pdfInteger(key: String): Long? = memScoped {
    val value = alloc<CGPDFIntegerVar>()

    if (CGPDFDictionaryGetInteger(this@pdfInteger, key, value.ptr)) value.value else null
}

@OptIn(ExperimentalForeignApi::class)
internal fun CGPDFDictionaryRef.pdfDictionary(key: String): CGPDFDictionaryRef? = memScoped {
    val value = alloc<CGPDFDictionaryRefVar>()

    if (CGPDFDictionaryGetDictionary(this@pdfDictionary, key, value.ptr)) value.value else null
}

@OptIn(ExperimentalForeignApi::class)
internal fun CGPDFDictionaryRef.pdfArray(key: String): CGPDFArrayRef? = memScoped {
    val value = alloc<CGPDFArrayRefVar>()

    if (CGPDFDictionaryGetArray(this@pdfArray, key, value.ptr)) value.value else null
}

@OptIn(ExperimentalForeignApi::class)
internal fun CGPDFDictionaryRef.pdfStream(key: String): CGPDFStreamRef? = memScoped {
    val value = alloc<CGPDFStreamRefVar>()

    if (CGPDFDictionaryGetStream(this@pdfStream, key, value.ptr)) value.value else null
}

@OptIn(ExperimentalForeignApi::class)
internal fun CGPDFDictionaryRef.pdfObject(key: String): CGPDFObjectRef? = memScoped {
    val value = alloc<CGPDFObjectRefVar>()

    if (CGPDFDictionaryGetObject(this@pdfObject, key, value.ptr)) value.value else null
}

@OptIn(ExperimentalForeignApi::class)
internal fun CGPDFArrayRef.pdfName(index: Int): String? = memScoped {
    val value = alloc<CPointerVar<ByteVar>>()

    if (CGPDFArrayGetName(this@pdfName, index.toULong(), value.ptr)) {
        value.value?.toKString()
    } else {
        null
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun CGPDFArrayRef.pdfInteger(index: Int): Long? = memScoped {
    val value = alloc<CGPDFIntegerVar>()

    if (CGPDFArrayGetInteger(this@pdfInteger, index.toULong(), value.ptr)) value.value else null
}

@OptIn(ExperimentalForeignApi::class)
internal fun CGPDFArrayRef.pdfStream(index: Int): CGPDFStreamRef? = memScoped {
    val value = alloc<CGPDFStreamRefVar>()

    if (CGPDFArrayGetStream(this@pdfStream, index.toULong(), value.ptr)) value.value else null
}

@OptIn(ExperimentalForeignApi::class)
internal fun CGPDFArrayRef.pdfString(index: Int): CGPDFStringRef? = memScoped {
    val value = alloc<CGPDFStringRefVar>()

    if (CGPDFArrayGetString(this@pdfString, index.toULong(), value.ptr)) value.value else null
}

@OptIn(ExperimentalForeignApi::class)
internal fun CGPDFArrayRef.pdfObject(index: Int): CGPDFObjectRef? = memScoped {
    val value = alloc<CGPDFObjectRefVar>()

    if (CGPDFArrayGetObject(this@pdfObject, index.toULong(), value.ptr)) value.value else null
}

@OptIn(ExperimentalForeignApi::class)
internal fun CGPDFObjectRef.asName(): String? = memScoped {
    val value = alloc<CPointerVar<ByteVar>>()

    if (CGPDFObjectGetValue(this@asName, kCGPDFObjectTypeName, value.ptr)) {
        value.value?.toKString()
    } else {
        null
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun CGPDFObjectRef.asArray(): CGPDFArrayRef? = memScoped {
    val value = alloc<CGPDFArrayRefVar>()

    if (CGPDFObjectGetValue(this@asArray, kCGPDFObjectTypeArray, value.ptr)) value.value else null
}

@OptIn(ExperimentalForeignApi::class)
internal fun CGPDFObjectRef.asStream(): CGPDFStreamRef? = memScoped {
    val value = alloc<CGPDFStreamRefVar>()

    if (CGPDFObjectGetValue(this@asStream, kCGPDFObjectTypeStream, value.ptr)) value.value else null
}

/**
 * The first [count] numbers of an array, or null unless every one of them is
 * there - a transformation missing a number is not a transformation.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun CGPDFArrayRef.pdfNumbers(count: Int): DoubleArray? = memScoped {
    val value = alloc<DoubleVar>()
    val numbers = DoubleArray(count)

    for (index in 0 until count) {
        if (!CGPDFArrayGetNumber(this@pdfNumbers, index.toULong(), value.ptr)) return null
        numbers[index] = value.value
    }

    numbers
}

/**
 * A PDF string's bytes. Not text: the one string this reads is an indexed
 * colour space's palette, which is a run of colour components that happens to
 * be written as a string.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun CGPDFStringRef.pdfBytes(): ByteArray =
    CGPDFStringGetBytePtr(this)?.readBytes(CGPDFStringGetLength(this).toInt()) ?: ByteArray(0)
