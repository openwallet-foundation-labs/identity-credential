package org.multipaz.mdoc.zkp.longfellow.util

import android.content.res.AssetManager
import kotlinx.io.bytestring.ByteString
import org.multipaz.mdoc.zkp.longfellow.LongfellowZkSystemBase

/**
 * Android-specific implementation of [LongfellowZkSystemBase] that loads ZK circuits
 * from the application's APK assets using the provided [AssetManager].
 *
 * Circuit files are expected to be stored under `assets/circuits/longfellow-libzk-v1/`,
 * with filenames following the convention: `<version>_<numAttributes>_<circuitHash>`.
 *
 * @param assetManager The [AssetManager] used to access APK-embedded circuit files.
 */
open class LongfellowZkSystem(
    private val assetManager: AssetManager
) : LongfellowZkSystemBase() {
    protected override fun getAllCircuitFileNames(): List<String> {
        val circuitsDirectory = "circuits/$name"
        return assetManager.list(circuitsDirectory)?.toList() ?: listOf()
    }

    protected override fun loadCircuit(circuitFileName: String): ByteString {
        val assetPath = "circuits/$name/$circuitFileName"
        val circuitBytes = assetManager.open(assetPath).use { inputStream ->
            inputStream.readBytes()
        }

        return ByteString(circuitBytes)
    }
}
