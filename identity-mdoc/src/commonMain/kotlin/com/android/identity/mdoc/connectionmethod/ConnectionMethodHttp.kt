package com.android.identity.mdoc.connectionmethod

import com.android.identity.cbor.Cbor.decode
import com.android.identity.cbor.Cbor.encode
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.CborMap

/**
 * Connection method for HTTP connections.
 *
 * @param uri the URI.
 */
class ConnectionMethodHttp(val uri: String): ConnectionMethod() {
    override fun toString(): String = "http:uri=$uri"

    override fun toDeviceEngagement(): ByteArray {
        val builder = CborMap.builder()
        builder.put(OPTION_KEY_URI, uri)
        return encode(
            CborArray.builder()
                .add(METHOD_TYPE)
                .add(METHOD_MAX_VERSION)
                .add(builder.end().build())
                .end().build()
        )
    }

    companion object {
        const val METHOD_TYPE = 4L
        const val METHOD_MAX_VERSION = 1L
        private const val OPTION_KEY_URI = 0L
        fun fromDeviceEngagementHttp(encodedDeviceRetrievalMethod: ByteArray): ConnectionMethodHttp? {
            val array = decode(encodedDeviceRetrievalMethod)
            val type = array[0].asNumber
            val version = array[1].asNumber
            require(type == METHOD_TYPE)
            if (version > METHOD_MAX_VERSION) {
                return null
            }
            val map = array[2]
            return ConnectionMethodHttp(map[OPTION_KEY_URI].asTstr)
        }
    }
}