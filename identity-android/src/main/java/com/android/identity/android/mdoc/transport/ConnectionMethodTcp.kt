package com.android.identity.android.mdoc.transport

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.CborMap
import com.android.identity.mdoc.connectionmethod.ConnectionMethod

class ConnectionMethodTcp(val host: String, val port: Int) : ConnectionMethod() {

    override fun toDeviceEngagement(): ByteArray =
        Cbor.encode(
            CborArray.builder()
                .add(METHOD_TYPE)
                .add(METHOD_MAX_VERSION)
                .addMap().apply {
                    put(OPTION_KEY_HOST, host)
                    put(OPTION_KEY_PORT, port.toLong())
                }.end()
                .end()
                .build()
        )



    override fun toString(): String = "tcp:host=$host:port=$port"

    companion object {
        // NOTE: 18013-5 only allows positive integers, but our codebase also supports negative
        // ones and this way we won't clash with types defined in the standard.
        const val METHOD_TYPE = -10L
        const val METHOD_MAX_VERSION = 1L

        private const val OPTION_KEY_HOST = 0L
        private const val OPTION_KEY_PORT = 1L

        @JvmStatic
        fun fromDeviceEngagementTcp(encodedDeviceRetrievalMethod: ByteArray): ConnectionMethodTcp? =
            Cbor.decode(encodedDeviceRetrievalMethod).asArray.let { array ->
                val version = array[1].asNumber
                if (version > METHOD_MAX_VERSION) {
                    return null
                }
                val type = array[0].asNumber
                require(type == METHOD_TYPE)

                val map = array[2]
                val host = map[OPTION_KEY_HOST].asTstr
                val port = map[OPTION_KEY_PORT].asNumber
                ConnectionMethodTcp(host, port.toInt())
            }
    }
}