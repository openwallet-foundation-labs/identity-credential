package com.android.identity.flow.environment

import kotlinx.io.bytestring.ByteString

/**
 * Simple resource-retrieval interface.
 */
interface Resources {
    fun getRawResource(name: String): ByteString?
    fun getStringResource(name: String): String?
}