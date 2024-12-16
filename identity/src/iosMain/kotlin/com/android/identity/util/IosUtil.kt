package com.android.identity.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.create
import platform.posix.memcpy

// Various iOS related utilities
//

@OptIn(ExperimentalForeignApi::class)
fun NSData.toByteArray(): ByteArray {
    return ByteArray(length.toInt()).apply {
        usePinned {
            memcpy(it.addressOf(0), bytes, length)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
fun ByteArray.toNSData(): NSData = memScoped {
    NSData.create(bytes = allocArrayOf(this@toNSData), length = this@toNSData.size.toULong())
}

fun NSError.toKotlinError(): Error {
    return Error("NSError domain=${this.domain} code=${this.code}: ${this.localizedDescription}")
}