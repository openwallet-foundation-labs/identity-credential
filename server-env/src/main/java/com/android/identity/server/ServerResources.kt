package org.multipaz.server

import org.multipaz.flow.server.Resources
import kotlinx.io.bytestring.ByteString

internal object ServerResources: Resources {
    override fun getRawResource(name: String): ByteString? {
        val stream = javaClass.getResourceAsStream("/resources/$name")
        return if (stream != null) ByteString(stream.readBytes()) else null
    }

    override fun getStringResource(name: String): String? {
        val stream = javaClass.getResourceAsStream("/resources/$name")
        return stream?.bufferedReader()?.readText()
    }
}