package org.multipaz.models.presentment

import org.multipaz.claim.Claim
import org.multipaz.claim.findMatchingClaim
import org.multipaz.credential.Credential
import org.multipaz.crypto.EcCurve
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.mdoc.credential.MdocCredential
import org.multipaz.mdoc.request.DeviceRequestParser
import org.multipaz.mdoc.util.toMdocRequest
import org.multipaz.mdoc.zkp.ZkSystem
import org.multipaz.mdoc.zkp.ZkSystemSpec
import org.multipaz.presentment.CredentialPresentmentSet
import org.multipaz.presentment.CredentialPresentmentData
import org.multipaz.presentment.SimpleCredentialPresentmentData
import org.multipaz.request.RequestedClaim
import org.multipaz.util.Logger

private const val TAG = "DeviceRequestParserExt"

/**
 * Generates a [CredentialPresentmentData] with matches for each [MdocCredential] satisfying the doc request.
 *
 * @param documentTypeRepository a [DocumentTypeRepository].
 * @param source a [PresentmentSource] to use as the source of truth for what credentials can be returned.
 * @param keyAgreementPossible if non-empty, a credential using Key Agreement may be returned provided
 *   its private key is one of the given curves.
 * @return a [CredentialPresentmentData] or `null` if no credentials satisfy the request.
 */
suspend fun DeviceRequestParser.DocRequest.getPresentmentData(
    documentTypeRepository: DocumentTypeRepository,
    source: PresentmentSource,
    keyAgreementPossible: List<EcCurve>,
): CredentialPresentmentData? {
    val zkRequested = zkSystemSpecs.isNotEmpty()
    val requestWithoutFiltering = toMdocRequest(
        documentTypeRepository = documentTypeRepository,
        mdocCredential = null
    )
    val documents = source.getDocumentsMatchingRequest(
        request = requestWithoutFiltering,
    )
    val matches = mutableListOf<Pair<Credential, Map<RequestedClaim, Claim>>>()
    for (document in documents) {
        var zkSystemMatch: ZkSystem? = null
        var zkSystemSpec: ZkSystemSpec? = null
        if (zkRequested) {
            val requesterSupportedZkSpecs = zkSystemSpecs
            val zkSystemRepository = source.zkSystemRepository
            if (zkSystemRepository != null) {
                // Find the first ZK System that the requester supports and matches the document
                for (zkSpec in requesterSupportedZkSpecs) {
                    val zkSystem = zkSystemRepository.lookup(zkSpec.system)
                    if (zkSystem == null) {
                        continue
                    }

                    val matchingZkSystemSpec = zkSystem.getMatchingSystemSpec(
                        zkSystemSpecs = requesterSupportedZkSpecs,
                        requestedClaims = requestWithoutFiltering.requestedClaims
                    )
                    if (matchingZkSystemSpec != null) {
                        zkSystemMatch = zkSystem
                        zkSystemSpec = matchingZkSystemSpec
                        break
                    }
                }
            }
        }
        if (zkRequested && zkSystemSpec == null) {
            Logger.w(TAG, "Reader requested ZK proof but no compatible ZkSpec was found.")
        }
        val mdocCredential = source.selectCredential(
            document = document,
            request = requestWithoutFiltering,
            // Check is zk is requested and a compatible ZK system spec was found
            keyAgreementPossible = if (zkRequested && zkSystemSpec != null) {
                listOf()
            } else {
                keyAgreementPossible
            }
        ) as MdocCredential?
        if (mdocCredential == null) {
            Logger.w(TAG, "No credential found")
            continue
        }

        val claims = mdocCredential.getClaims(documentTypeRepository)
        val claimsToShow = buildMap {
            for (requestedClaim in requestWithoutFiltering.requestedClaims) {
                claims.findMatchingClaim(requestedClaim)?.let {
                    put(requestedClaim as RequestedClaim, it)
                }
            }
        }
        matches.add(Pair(mdocCredential,claimsToShow))
    }
    return SimpleCredentialPresentmentData(matches)
}
