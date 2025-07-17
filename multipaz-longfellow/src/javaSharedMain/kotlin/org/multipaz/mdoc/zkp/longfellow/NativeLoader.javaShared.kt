package org.multipaz.mdoc.zkp.longfellow

/**
 * Loads native libraries.
 *
 * Used to allow each Java-based system (Android, JVM) to implement this method per their needs.
 */
internal expect object NativeLoader {
    fun loadLibrary(libraryName: String)
}
