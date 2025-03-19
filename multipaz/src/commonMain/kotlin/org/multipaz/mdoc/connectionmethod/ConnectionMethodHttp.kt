package org.multipaz.mdoc.connectionmethod

import org.multipaz.cbor.Cbor
import org.multipaz.cbor.addCborMap
import org.multipaz.cbor.buildCborArray
import org.multipaz.mdoc.transport.MdocTransport
import org.multipaz.nfc.NdefRecord
import org.multipaz.util.Logger

/**
 * Connection method for HTTP connections.
 *
 * @param uri the URI.
 */
class ConnectionMethodHttp(val uri: String): ConnectionMethod() {
    override fun equals(other: Any?): Boolean {
        return other is ConnectionMethodHttp && other.uri == uri
    }

    override fun toString(): String = "http:uri=$uri"

    override fun toDeviceEngagement(): ByteArray {
        return Cbor.encode(
            buildCborArray {
                add(METHOD_TYPE)
                add(METHOD_MAX_VERSION)
                addCborMap {
                    put(OPTION_KEY_URI, uri)
                }
            }
        )
    }

    override fun toNdefRecord(
        auxiliaryReferences: List<String>,
        role: MdocTransport.Role,
        skipUuids: Boolean
    ): Pair<NdefRecord, NdefRecord>? {
        Logger.w(TAG, "toNdefRecord() not yet implemented")
        return null
    }

    companion object {
        private const val TAG = "ConnectionMethodHttp"
        const val METHOD_TYPE = 4L
        const val METHOD_MAX_VERSION = 1L
        private const val OPTION_KEY_URI = 0L

        internal fun fromDeviceEngagement(encodedDeviceRetrievalMethod: ByteArray): ConnectionMethodHttp? {
            val array = Cbor.decode(encodedDeviceRetrievalMethod)
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