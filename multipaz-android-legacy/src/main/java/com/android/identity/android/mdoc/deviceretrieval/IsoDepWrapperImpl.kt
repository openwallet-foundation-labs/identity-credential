package com.android.identity.android.mdoc.deviceretrieval

import android.nfc.Tag
import android.nfc.tech.IsoDep
import java.io.IOException


class IsoDepWrapperImpl(tag: Tag?) : IsoDepWrapper {
    private var mIsoDep: IsoDep? = null

    init {
        mIsoDep = IsoDep.get(tag)
    }

    override fun getTag(): Tag {
        return mIsoDep!!.tag
    }

    override val isConnected: Boolean
        get() {
            return mIsoDep!!.isConnected
        }

    @Throws(IOException::class)
    override fun connect() {
        mIsoDep!!.connect()
    }

    @Throws(IOException::class)
    override fun close() {
        mIsoDep!!.close()
    }

    override fun getIsoDep(tag: Tag?) {
        mIsoDep = IsoDep.get(tag)
    }

    override val isTagSupported: Boolean
        get() {
            return mIsoDep != null
        }

    override fun setTimeout(timeout: Int) {
        mIsoDep!!.timeout = timeout
    }

    override fun getTimeout(): Int {
        return mIsoDep!!.timeout
    }

    override fun getHistoricalBytes(): ByteArray {
        return mIsoDep!!.historicalBytes
    }

    override fun getHiLayerResponse(): ByteArray {
        return mIsoDep!!.hiLayerResponse
    }

    override val maxTransceiveLength: Int
        get() {
            // This value is set based on the Pixel's eSE APDU Buffer size
            return 261
        }

    @Throws(IOException::class)
    override fun transceive(data: ByteArray?): ByteArray {
        return mIsoDep!!.transceive(data)
    }

    override val isExtendedLengthApduSupported: Boolean
        get() {
            return mIsoDep!!.isExtendedLengthApduSupported
        }
}