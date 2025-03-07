package com.android.identity.android.mdoc.transport

import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborArray
import org.multipaz.cbor.CborMap
import org.multipaz.mdoc.connectionmethod.ConnectionMethod
import org.multipaz.mdoc.transport.MdocTransport
import org.multipaz.nfc.NdefRecord
import org.multipaz.util.Logger

class ConnectionMethodUdp(val host: String, val port: Int) : ConnectionMethod() {

    override fun toDeviceEngagement(): ByteArray {
        val mapBuilder = CborMap.builder()
        mapBuilder.put(OPTION_KEY_HOST, host)
        mapBuilder.put(OPTION_KEY_PORT, port.toLong())
        return Cbor.encode(
            CborArray.builder()
                .add(METHOD_TYPE)
                .add(METHOD_MAX_VERSION)
                .add(mapBuilder.end().build())
                .end()
                .build()
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

    override fun equals(other: Any?): Boolean {
        return other is ConnectionMethodUdp && other.host == host && other.port == port
    }

    override fun toString(): String {
        return "udp:host=$host:port=$port"
    }

    companion object {
        private const val TAG = "ConnectionMethodUdp"

        // NOTE: 18013-5 only allows positive integers, but our codebase also supports negative
        // ones and this way we won't clash with types defined in the standard.
        const val METHOD_TYPE = -11L
        const val METHOD_MAX_VERSION = 1L

        private const val OPTION_KEY_HOST = 0L
        private const val OPTION_KEY_PORT = 1L
        @JvmStatic
        fun fromDeviceEngagementUdp(encodedDeviceRetrievalMethod: ByteArray): ConnectionMethodUdp? {
            val array = Cbor.decode(encodedDeviceRetrievalMethod).asArray
            val type = array[0].asNumber
            val version = array[1].asNumber
            require(type == METHOD_TYPE)
            if (version > METHOD_MAX_VERSION) {
                return null
            }
            val map = array[2]
            val host = map[OPTION_KEY_HOST].asTstr
            val port = map[OPTION_KEY_PORT].asNumber
            return ConnectionMethodUdp(host, port.toInt())
        }
    }
}
