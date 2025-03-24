package org.multipaz.android.direct_access

import android.nfc.Tag
import com.android.identity.android.mdoc.deviceretrieval.IsoDepWrapper
import java.io.IOException


class ShadowIsoDep : IsoDepWrapper {
    val TECH_ISO_DEP: String = "android.nfc.tech.IsoDep"

    private var mTimeout = 0

    override fun getTag(): Tag? {
        //android.nfc.tech.IsoDep
        //
        return null
    }

    override fun getIsoDep(tag: Tag?) {
    }

    override val isConnected: Boolean
        get() {
            try {
                return DirectAccessSmartCardTransport.isConnected
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }

    @Throws(IOException::class)
    override fun connect() {
        DirectAccessSmartCardTransport.openConnection()
        DirectAccessSmartCardTransport.openConnection()
    }

    @Throws(IOException::class)
    override fun close() {
        DirectAccessSmartCardTransport.closeConnection()
    }

    override val isTagSupported: Boolean
        get() {
            return true
        }

    override fun setTimeout(timeout: Int) {
        mTimeout = timeout
    }

    override fun getTimeout(): Int {
        return mTimeout
    }

    override fun getHistoricalBytes(): ByteArray {
        return ByteArray(0)
    }

    override fun getHiLayerResponse(): ByteArray {
        return ByteArray(0)
    }

    override val maxTransceiveLength: Int
        get() {
            return DirectAccessSmartCardTransport.maxTransceiveLength
        }

    @Throws(IOException::class)
    override fun transceive(data: ByteArray?): ByteArray {
        return data?.let { DirectAccessSmartCardTransport.sendData(it) }!!
    }

    override val isExtendedLengthApduSupported: Boolean
        get() {
            return true
        }
}