package org.multipaz.issuance.tunnel

import org.multipaz.issuance.evidence.EvidenceRequestIcaoNfcTunnel
import org.multipaz.issuance.evidence.EvidenceRequestIcaoNfcTunnelType
import org.multipaz.mrtd.MrtdAccessData

/**
 * Caching factory for [MrtdNfcTunnel].
 *
 * Once [MrtdNfcTunnel] is created, it exists for a while and can be identified by its
 * [MrtdNfcTunnel.token].
 */
interface MrtdNfcTunnelFactory {
    /**
     * Initialize, supplying the desired data groups that should be read from the chip and,
     * optionally, data to access the chip (must be provided when the tunnel was established using
     * [EvidenceRequestIcaoNfcTunnelType.HANDSHAKE] with
     * [EvidenceRequestIcaoNfcTunnel.passThrough] set to true).
     */
    fun acquire(dataGroups: List<Int>, accessData: MrtdAccessData?): MrtdNfcTunnel

    /**
     * Find a previously acquired [MrtdNfcTunnel] by its [MrtdNfcTunnel.token].
     */
    fun getByToken(token: String): MrtdNfcTunnel
}