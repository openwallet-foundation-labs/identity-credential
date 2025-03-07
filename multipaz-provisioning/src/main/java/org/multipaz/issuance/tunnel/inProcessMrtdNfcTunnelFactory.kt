package org.multipaz.issuance.tunnel

import org.multipaz.mrtd.MrtdAccessData
import kotlin.time.Duration.Companion.minutes

/**
 * Factory for [MrtdNfcTunnel] that are suitable for use in single-process servers.
 */
object inProcessMrtdNfcTunnelFactory: MrtdNfcTunnelFactory {
    private val lock = Object()
    private var counter = 0
    private val tunnelMap = mutableMapOf<String, InProcessMrtdNfcTunnel>()

    override fun acquire(dataGroups: List<Int>, accessData: MrtdAccessData?): MrtdNfcTunnel {
        val token = synchronized(lock) { (counter++).toString() }
        return InProcessMrtdNfcTunnel(
            token,
            5.minutes,
            dataGroups,
            accessData,
            { instance -> synchronized(lock) { tunnelMap[token] = instance } },
            { synchronized(lock) { tunnelMap.remove(token) } }
        )
    }

    override fun getByToken(token: String): MrtdNfcTunnel {
        return synchronized(lock) { tunnelMap[token] }!!
    }
}