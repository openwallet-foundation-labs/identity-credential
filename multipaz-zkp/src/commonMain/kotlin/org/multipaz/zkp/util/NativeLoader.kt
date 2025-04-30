package org.multipaz.zkp.util

/**
 * Loads native libraries
 * Used to allow each system (Android, JVM, iOS) to implement this method per their needs.
 */
internal expect object NativeLoader {
    fun loadLibrary(libraryName: String)
}
