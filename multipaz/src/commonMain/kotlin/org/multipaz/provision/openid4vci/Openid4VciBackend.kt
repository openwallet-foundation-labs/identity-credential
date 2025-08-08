package org.multipaz.provision.openid4vci

import org.multipaz.provision.ProvisioningClient
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.securearea.KeyAttestation

/**
 * Interface to the wallet back-end functionality, required by Openid4Vci.
 *
 * Openid4Vci trust model includes wallet back-end (typically implemented as a server, although
 * this is not mandated). Openid4vci provisioning server does not have trust relationship with
 * the wallet application itself (as establishing such trust involves platform-specific
 * assertions/attestations, which are not standardized). Instead, provisioning server trusts
 * the wallet back-end, and the wallet back-end, in turn, establishes trust with the
 * wallet application in implementation-specific manner.
 *
 * This interface exposes back-end functionality for use in Openid4Vci [ProvisioningClient].
 *
 * An implementation of this interface must be available in [BackendEnvironment] associated
 * with the coroutine context of the [ProvisioningClient] asynchronous calls.
 */
interface Openid4VciBackend {
    /**
     * Creates fresh OAuth JWT client assertion based on the server-side key.
     */
    suspend fun createJwtClientAssertion(tokenUrl: String): String

    /**
     * Creates OAuth JWT wallet attestation based on the mobile-platform-specific [KeyAttestation].
     */
    suspend fun createJwtWalletAttestation(keyAttestation: KeyAttestation): String

    /**
     * Creates OAuth JWT key attestation based on the given list of mobile-platform-specific
     * [KeyAttestation]s.
     */
    suspend fun createJwtKeyAttestation(
        keyAttestations: List<KeyAttestation>,
        challenge: String
    ): String
}