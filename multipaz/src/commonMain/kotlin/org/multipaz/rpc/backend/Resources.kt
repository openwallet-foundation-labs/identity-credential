package org.multipaz.rpc.backend

import kotlinx.io.bytestring.ByteString

/**
 * Simple resource-retrieval interface.
 */
interface Resources {
    fun getRawResource(name: String): ByteString?
    fun getStringResource(name: String): String?
}