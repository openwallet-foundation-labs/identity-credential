package org.multipaz.provisioning

import org.multipaz.rpc.annotation.RpcInterface
import org.multipaz.rpc.annotation.RpcMethod
import org.multipaz.provisioning.evidence.EvidenceRequest
import org.multipaz.provisioning.evidence.EvidenceResponse

/**
 * A flow used for identifying and proofing an application.
 */
@RpcInterface
interface Proofing {
    /**
     * Called to get a set of evidence requests from the issuer.
     *
     * This is the first method that should be called in the flow.
     *
     * If this returns the empty list it means that proofing is completed and the application
     * should call [complete]. Otherwise the application needs to return a single piece
     * of evidence matching one of the provided requests. Use [sendEvidence] to return the evidence.
     *
     * @return an empty list if no more evidence is required, otherwise a list of evidence requests.
     */
    @RpcMethod
    suspend fun getEvidenceRequests(): List<EvidenceRequest>

    /**
     * Sends evidence to the issuer.
     *
     * After this is called, the application should call [getEvidenceRequests] to see
     * if more evidence is required.
     *
     * @param evidenceResponse the evidence or null if none of the requested evidence can be returned.
     */
    @RpcMethod
    suspend fun sendEvidence(evidenceResponse: EvidenceResponse)
}