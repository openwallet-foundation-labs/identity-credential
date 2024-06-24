package com.android.identity.wallet.server

import com.android.identity.flow.server.Resources
import kotlinx.io.bytestring.ByteString
import java.io.File

class ServerResources: Resources {
    override fun getRawResource(name: String): ByteString? {
        val stream = javaClass.getResourceAsStream("/resources/$name")
        return if (stream != null) ByteString(stream.readBytes()) else null
    }

    override fun getStringResource(name: String): String? {
        val stream = javaClass.getResourceAsStream("/resources/$name")
        return stream?.bufferedReader()?.readText()
    }

}