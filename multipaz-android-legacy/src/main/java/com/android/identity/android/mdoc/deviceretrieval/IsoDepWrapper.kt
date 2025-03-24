package com.android.identity.android.mdoc.deviceretrieval

import android.nfc.Tag
import java.io.IOException



interface IsoDepWrapper {

    fun getTag(): Tag?

    fun getIsoDep(tag: Tag?)

    @Throws(IOException::class)
    fun connect()

    @Throws(IOException::class)
    fun close()

    fun setTimeout(timeout: Int)

    fun getTimeout(): Int

    fun getHistoricalBytes(): ByteArray?

    fun getHiLayerResponse(): ByteArray?

    @Throws(IOException::class)
    fun transceive(data: ByteArray?): ByteArray

    val isExtendedLengthApduSupported : Boolean

    val isConnected : Boolean

    val isTagSupported : Boolean

    val  maxTransceiveLength: Int
}