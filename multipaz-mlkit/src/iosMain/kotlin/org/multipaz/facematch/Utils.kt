package org.multipaz.facematch

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSError

/** Helps to setup error handler. Throws on error in the closure. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Suppress("TooGenericExceptionThrown")
internal fun <T> errorHandled(block: (CPointer<ObjCObjectVar<NSError?>>) -> T?): T? {
    val (result, error) = memScoped {
        val errorPtr = alloc<ObjCObjectVar<NSError?>>()
        runCatching {
            block(errorPtr.ptr)
        }.getOrNull() to errorPtr.value
    }
    if (error != null) throw Exception(error.description)
    return result
}