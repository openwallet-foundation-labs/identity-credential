package org.multipaz.util

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.create
import platform.posix.memcpy
import kotlin.time.Clock
import kotlin.time.Instant

// Various iOS related utilities
//

@OptIn(ExperimentalForeignApi::class)
fun NSData.toByteArray(): ByteArray {
    return if (length == 0UL) {
        byteArrayOf()
    } else {
        ByteArray(length.toInt()).apply {
            usePinned {
                memcpy(it.addressOf(0), bytes, length)
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
fun ByteArray.toNSData(): NSData = memScoped {
    NSData.create(bytes = allocArrayOf(this@toNSData), length = this@toNSData.size.toULong())
}

fun NSError.toKotlinError(): Error {
    return Error("NSError domain=${this.domain} code=${this.code}: ${this.localizedDescription}")
}

fun NSDate.toKotlinInstant(): Instant {
    return toKotlinInstant()
}

fun Clock.Companion.getSystem(): Clock = Clock.System
