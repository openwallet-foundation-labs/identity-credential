package org.multipaz.facematch

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.multipaz.util.Logger
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithBytesNoCopy
import platform.Foundation.stringByAppendingPathComponent
import platform.Foundation.writeToFile

private const val TAG = "LiteRtModel"

@OptIn(ExperimentalForeignApi::class)
internal actual fun saveBytesToFileSystem(
    fileName: String,
    data: ByteArray,
    forceOverwrite: Boolean
): String? =
    try {
        val fileManager = NSFileManager.defaultManager
        val cachesDirectory = NSSearchPathForDirectoriesInDomains(
            NSCachesDirectory,
            NSUserDomainMask,
            true
        ).firstOrNull() as? NSString

        if (cachesDirectory == null) {
            Logger.e(TAG, "Error: Could not find Caches directory on iOS.")
            null
        }

        val filePath = cachesDirectory!!.stringByAppendingPathComponent(fileName)

        // Return if the file already created.
        if (fileManager.fileExistsAtPath(filePath) && !forceOverwrite) {
            filePath
        }

        val nsData = data.usePinned { pinned ->
            NSData.dataWithBytesNoCopy(
                bytes = pinned.addressOf(0),
                length = data.size.toULong(),
                freeWhenDone = false // The data is managed by the Kotlin ByteArray
            )
        }

        val success = nsData.writeToFile(filePath, true)

        if (success) {
            filePath
        } else {
            Logger.e(TAG, "Failed to write file '$fileName' to $filePath on iOS.")
            null
        }
    } catch (e: Exception) {
        Logger.e(TAG, "Error saving file '$fileName' to iOS file system: ${e.message}")
        e.printStackTrace()
        null
    }
