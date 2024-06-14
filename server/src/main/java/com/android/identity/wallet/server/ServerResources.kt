package com.android.identity.wallet.server

import com.android.identity.flow.server.Resources
import kotlinx.io.bytestring.ByteString
import java.io.File

class ServerResources(private val resourceDir: String): Resources {
    override fun getRawResource(name: String): ByteString? {
        val file = fileFor(name)
        return if (file.canRead()) ByteString(file.inputStream().readBytes()) else null
    }

    override fun getStringResource(name: String): String? {
        val file = fileFor(name)
        return if (file.canRead()) file.bufferedReader().readText() else null
    }

    private fun fileFor(name: String): File {
        return File("$resourceDir/$name")
    }
}