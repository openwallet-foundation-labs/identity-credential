package org.multipaz.mdoc.zkp.longfellow.util

internal actual object NativeLoader {
    actual fun loadLibrary(libraryName: String) {
        kotlin.runCatching {
            System.loadLibrary(libraryName)
        }.onFailure { error ->
            throw RuntimeException("Failed to load native library '$libraryName'", error)
        }
    }
}
